package com.refnote.service;

import com.refnote.entity.Document;
import com.refnote.entity.PdfBlock;
import com.refnote.repository.DocumentRepository;
import com.refnote.repository.PdfBlockRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PdfParsingService {

    private final PdfBlockRepository pdfBlockRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final ExplanationService explanationService;
    private final TransactionTemplate transactionTemplate;

    public PdfParsingService(PdfBlockRepository pdfBlockRepository,
                             DocumentRepository documentRepository,
                             FileStorageService fileStorageService,
                             ExplanationService explanationService,
                             PlatformTransactionManager transactionManager) {
        this.pdfBlockRepository = pdfBlockRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.explanationService = explanationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("taskExecutor")
    public void parseAndGenerate(Long documentId) {
        try {
            doUpdateStatus(documentId, Document.DocumentStatus.PARSING);

            doParseAndSaveBlocks(documentId);

            doUpdateStatus(documentId, Document.DocumentStatus.GENERATING);

            explanationService.generateExplanations(documentId);

        } catch (Exception e) {
            log.error("PDF 파싱/해설 생성 실패 - 문서 {}", documentId, e);
            doUpdateStatus(documentId, Document.DocumentStatus.FAILED);
        }
    }

    private void doUpdateStatus(Long documentId, Document.DocumentStatus status) {
        transactionTemplate.executeWithoutResult(txStatus -> {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다: " + documentId));
            document.setStatus(status);
            documentRepository.save(document);
        });
    }

    private void doParseAndSaveBlocks(Long documentId) {
        transactionTemplate.executeWithoutResult(txStatus -> {
            try {
                Document document = documentRepository.findById(documentId)
                        .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다: " + documentId));

                InputStream pdfStream = fileStorageService.download(document.getS3Key());
                List<PdfBlock> blocks = extractBlocks(pdfStream, document);

                pdfBlockRepository.saveAll(blocks);
                documentRepository.save(document);
                log.info("PDF 파싱 완료 - 문서 {}: {}개 블록 추출", documentId, blocks.size());
            } catch (IOException e) {
                throw new RuntimeException("PDF 파싱 실패", e);
            }
        });
    }

    private List<PdfBlock> extractBlocks(InputStream pdfStream, Document document) throws IOException {
        List<PdfBlock> blocks = new ArrayList<>();

        try (PDDocument pdDocument = Loader.loadPDF(pdfStream.readAllBytes())) {
            int totalPages = pdDocument.getNumberOfPages();
            document.setPageCount(totalPages);

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                PDPage page = pdDocument.getPage(pageNum - 1);
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();

                BlockExtractor extractor = new BlockExtractor(pageNum);
                extractor.setStartPage(pageNum);
                extractor.setEndPage(pageNum);
                extractor.getText(pdDocument);

                List<TextBlock> textBlocks = extractor.getTextBlocks();

                int blockOrder = 0;
                for (TextBlock textBlock : textBlocks) {
                    if (textBlock.text.isBlank()) continue;

                    blockOrder++;
                    PdfBlock.BlockType blockType = detectBlockType(textBlock.text);

                    PdfBlock pdfBlock = PdfBlock.builder()
                            .document(document)
                            .pageNumber(pageNum)
                            .blockOrder(blockOrder)
                            .content(textBlock.text.trim())
                            .blockType(blockType)
                            .x((double) textBlock.x / pageWidth)
                            .y((double) textBlock.y / pageHeight)
                            .width((double) textBlock.width / pageWidth)
                            .height((double) textBlock.height / pageHeight)
                            .build();

                    blocks.add(pdfBlock);
                }
            }
        }

        return blocks;
    }

    private PdfBlock.BlockType detectBlockType(String text) {
        String trimmed = text.trim();
        if (trimmed.matches("^(#{1,3}|\\d+\\.|[A-Z]{2,})\\s+.*") || trimmed.length() < 80 && trimmed.equals(trimmed.toUpperCase())) {
            return PdfBlock.BlockType.HEADING;
        }
        if (trimmed.contains("\\frac") || trimmed.contains("\\int") || trimmed.contains("\\sum")
                || trimmed.matches(".*[=+\\-*/^].*\\d+.*[=+\\-*/^].*")) {
            return PdfBlock.BlockType.FORMULA;
        }
        if (trimmed.contains("|") && trimmed.split("\\|").length > 3) {
            return PdfBlock.BlockType.TABLE;
        }
        return PdfBlock.BlockType.TEXT;
    }

    private static class TextBlock {
        String text;
        float x, y, width, height;

        TextBlock(String text, float x, float y, float width, float height) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static class BlockExtractor extends PDFTextStripper {
        private final int targetPage;
        private final List<TextBlock> textBlocks = new ArrayList<>();
        private final StringBuilder currentBlock = new StringBuilder();
        private float blockX = Float.MAX_VALUE;
        private float blockY = Float.MAX_VALUE;
        private float blockMaxX = 0;
        private float blockMaxY = 0;
        private float lastY = -1;
        private static final float LINE_GAP_THRESHOLD = 15.0f;

        BlockExtractor(int targetPage) throws IOException {
            super();
            this.targetPage = targetPage;
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) {
            if (textPositions.isEmpty()) return;

            TextPosition first = textPositions.get(0);
            TextPosition last = textPositions.get(textPositions.size() - 1);

            float lineY = first.getYDirAdj();

            if (lastY >= 0 && Math.abs(lineY - lastY) > LINE_GAP_THRESHOLD) {
                flushBlock();
            }

            for (TextPosition tp : textPositions) {
                float x = tp.getXDirAdj();
                float y = tp.getYDirAdj();
                float w = tp.getWidthDirAdj();
                float h = tp.getHeightDir();

                blockX = Math.min(blockX, x);
                blockY = Math.min(blockY, y - h);
                blockMaxX = Math.max(blockMaxX, x + w);
                blockMaxY = Math.max(blockMaxY, y);
            }

            currentBlock.append(string).append(" ");
            lastY = lineY;
        }

        @Override
        protected void writeLineSeparator() {
            currentBlock.append("\n");
        }

        @Override
        protected void endPage(org.apache.pdfbox.pdmodel.PDPage page) {
            flushBlock();
        }

        private void flushBlock() {
            String text = currentBlock.toString().trim();
            if (!text.isEmpty()) {
                textBlocks.add(new TextBlock(text, blockX, blockY, blockMaxX - blockX, blockMaxY - blockY));
            }
            currentBlock.setLength(0);
            blockX = Float.MAX_VALUE;
            blockY = Float.MAX_VALUE;
            blockMaxX = 0;
            blockMaxY = 0;
        }

        List<TextBlock> getTextBlocks() {
            return textBlocks;
        }
    }
}
