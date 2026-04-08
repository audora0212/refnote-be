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
    private final ClaudeApiService claudeApiService;
    private final TransactionTemplate transactionTemplate;

    public PdfParsingService(PdfBlockRepository pdfBlockRepository,
                             DocumentRepository documentRepository,
                             FileStorageService fileStorageService,
                             ExplanationService explanationService,
                             ClaudeApiService claudeApiService,
                             PlatformTransactionManager transactionManager) {
        this.pdfBlockRepository = pdfBlockRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.explanationService = explanationService;
        this.claudeApiService = claudeApiService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("taskExecutor")
    public void parseAndGenerate(Long documentId) {
        try {
            // 분류 단계
            doUpdateStatus(documentId, Document.DocumentStatus.ANALYZING);
            boolean isStudyMaterial = classifyAndSave(documentId);

            if (!isStudyMaterial) {
                doUpdateStatus(documentId, Document.DocumentStatus.REJECTED);
                return;
            }

            // 기존 파싱 + 해설 생성 흐름
            doUpdateStatus(documentId, Document.DocumentStatus.PARSING);

            doParseAndSaveBlocks(documentId);

            doUpdateStatus(documentId, Document.DocumentStatus.GENERATING);

            explanationService.generateExplanations(documentId);

        } catch (Exception e) {
            log.error("PDF 파싱/해설 생성 실패 - 문서 {}", documentId, e);
            doUpdateStatus(documentId, Document.DocumentStatus.FAILED);
        }
    }

    /**
     * PDF 첫 3페이지 텍스트를 추출하여 Claude API로 분류 요청.
     * 결과를 Document 엔티티에 저장.
     * @return true면 학습자료, false면 비학습자료
     */
    private boolean classifyAndSave(Long documentId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(txStatus -> {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다: " + documentId));

            String sampleText = extractSampleText(document);
            ClaudeApiService.ClassificationResult result = claudeApiService.classifyDocument(sampleText);

            document.setIsStudyMaterial(result.isStudyMaterial());
            document.setEstimatedSubject(result.estimatedSubject());
            document.setEstimatedDifficulty(result.estimatedDifficulty());
            document.setDocumentType(result.documentType());
            document.setRejectionReason(result.rejectionReason());
            documentRepository.save(document);

            return result.isStudyMaterial();
        }));
    }

    /**
     * PDF 전체 텍스트에서 앞 1500자 + 중간 1000자 + 끝 500자를 조합하여 분류용 샘플 텍스트를 생성한다.
     * 문서가 3000자 이하이면 전문을 반환한다.
     */
    private String extractSampleText(Document document) {
        try {
            InputStream pdfStream = fileStorageService.download(document.getS3Key());
            try (PDDocument pdDocument = Loader.loadPDF(pdfStream.readAllBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(pdDocument.getNumberOfPages());
                String fullText = stripper.getText(pdDocument);

                // 짧은 문서는 전문 사용
                if (fullText.length() <= 3000) {
                    return fullText;
                }

                // 앞 1500자 + 중간 1000자 + 끝 500자
                String head = fullText.substring(0, 1500);

                int midStart = (fullText.length() - 1000) / 2;
                String middle = fullText.substring(midStart, midStart + 1000);

                String tail = fullText.substring(fullText.length() - 500);

                return head + "\n---\n" + middle + "\n---\n" + tail;
            }
        } catch (IOException e) {
            log.warn("분류용 텍스트 추출 실패 - 문서 {}: {}", document.getId(), e.getMessage());
            return "";
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
                // 1) 항상 heuristic으로 좌표 포함 블록 추출 (좌표 확보용)
                List<PdfBlock> heuristicBlocks = extractBlocksHeuristic(pdDocument, document, pageNum);

                if (heuristicBlocks.isEmpty()) continue;

                // 2) 페이지 전체 텍스트 추출
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(pdDocument).trim();

                // 3) AI 블록 분리 시도 (200자 이상일 때만)
                List<ClaudeApiService.SplitBlock> aiBlocks = null;
                if (pageText.length() >= 200) {
                    try {
                        aiBlocks = claudeApiService.splitBlocksWithAI(pageText);
                    } catch (Exception e) {
                        log.warn("AI 블록 분리 호출 예외, heuristic 폴백 - 페이지 {}: {}", pageNum, e.getMessage());
                    }
                }

                if (aiBlocks != null && !aiBlocks.isEmpty()) {
                    // AI 블록 분리 성공: AI 블록의 텍스트를 heuristic 블록의 좌표에 매핑
                    int blockOrder = 0;
                    for (ClaudeApiService.SplitBlock ab : aiBlocks) {
                        if ("SKIP".equalsIgnoreCase(ab.type())) continue;
                        if (ab.content() == null || ab.content().isBlank()) continue;

                        blockOrder++;
                        PdfBlock.BlockType blockType = mapAiBlockType(ab.type());

                        // AI 블록 텍스트와 가장 잘 매칭되는 heuristic 블록의 좌표를 사용
                        double bestX = 0.05, bestY = 0, bestW = 0.9, bestH = 0.1;
                        double minYMatch = Double.MAX_VALUE;
                        double maxYMatch = 0;
                        String aiContent = ab.content().trim();

                        for (PdfBlock hb : heuristicBlocks) {
                            String hbContent = hb.getContent().trim();
                            // AI 블록 텍스트가 heuristic 블록 텍스트를 포함하거나, 첫 30자가 겹치면 매칭
                            String aiStart = aiContent.substring(0, Math.min(30, aiContent.length()));
                            if (hbContent.contains(aiStart) || aiContent.contains(hbContent.substring(0, Math.min(30, hbContent.length())))) {
                                double hbTop = hb.getY();
                                double hbBottom = hb.getY() + hb.getHeight();
                                if (hbTop < minYMatch) {
                                    minYMatch = hbTop;
                                    bestX = hb.getX();
                                    bestW = hb.getWidth();
                                }
                                if (hbBottom > maxYMatch) {
                                    maxYMatch = hbBottom;
                                }
                            }
                        }

                        // 매칭된 좌표가 없으면 순서 기반 추정
                        if (minYMatch == Double.MAX_VALUE) {
                            int idx = blockOrder - 1;
                            int total = (int) aiBlocks.stream().filter(b -> !"SKIP".equalsIgnoreCase(b.type())).count();
                            if (total > 0 && !heuristicBlocks.isEmpty()) {
                                // heuristic 블록들의 전체 범위 안에서 비례 배분
                                double pageMinY = heuristicBlocks.stream().mapToDouble(PdfBlock::getY).min().orElse(0);
                                double pageMaxY = heuristicBlocks.stream().mapToDouble(b -> b.getY() + b.getHeight()).max().orElse(1);
                                double range = pageMaxY - pageMinY;
                                bestY = pageMinY + (range * idx / total);
                                bestH = range / total;
                                bestX = heuristicBlocks.get(0).getX();
                                bestW = heuristicBlocks.get(0).getWidth();
                            }
                            minYMatch = bestY;
                            maxYMatch = bestY + bestH;
                        }

                        PdfBlock pdfBlock = PdfBlock.builder()
                                .document(document)
                                .pageNumber(pageNum)
                                .blockOrder(blockOrder)
                                .content(aiContent)
                                .blockType(blockType)
                                .x(bestX)
                                .y(minYMatch)
                                .width(bestW)
                                .height(Math.max(maxYMatch - minYMatch, 0.02))
                                .build();

                        blocks.add(pdfBlock);
                    }
                    log.debug("AI 블록 분리 성공 (좌표 매핑) - 페이지 {}: {}개 블록", pageNum, blockOrder);
                } else {
                    // 폴백: heuristic 블록 그대로 사용
                    blocks.addAll(heuristicBlocks);
                }
            }
        }

        return blocks;
    }

    /**
     * AI 블록 type 문자열을 PdfBlock.BlockType으로 매핑
     */
    private PdfBlock.BlockType mapAiBlockType(String aiType) {
        if (aiType == null) return PdfBlock.BlockType.TEXT;
        return switch (aiType.toUpperCase()) {
            case "HEADING" -> PdfBlock.BlockType.HEADING;
            case "FORMULA" -> PdfBlock.BlockType.FORMULA;
            case "TABLE" -> PdfBlock.BlockType.TABLE;
            case "IMAGE" -> PdfBlock.BlockType.IMAGE;
            default -> PdfBlock.BlockType.TEXT;
        };
    }

    /**
     * 기존 heuristic Y좌표 gap 기반 블록 분리 (폴백용)
     */
    private List<PdfBlock> extractBlocksHeuristic(PDDocument pdDocument, Document document, int pageNum) throws IOException {
        List<PdfBlock> blocks = new ArrayList<>();
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
        private static final float LINE_GAP_THRESHOLD = 8.0f;

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
