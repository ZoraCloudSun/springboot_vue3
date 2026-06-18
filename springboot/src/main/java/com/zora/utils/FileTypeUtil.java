package com.zora.utils;

import com.zora.exception.BadRequestException;

/**
 * 文件类型检测工具类（Phase 2: RAG 知识库）
 * <p>
 * 根据文件扩展名判断文件类型，并进行大小校验。
 * 支持的格式：PDF、DOCX、DOC、TXT、MD
 * </p>
 */
public class FileTypeUtil {

    /** 支持的文件扩展名 */
    private static final String[] SUPPORTED_EXTENSIONS = { ".pdf", ".docx", ".doc", ".txt", ".md" };

    private FileTypeUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 根据文件名检测文件类型
     *
     * @param filename 原始文件名（如 "readme.md"）
     * @return 文件类型小写标识（pdf / docx / doc / txt / md）
     * @throws BadRequestException 如果文件类型不支持
     */
    public static String detectFileType(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new BadRequestException("文件名为空");
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))
            return "pdf";
        if (lower.endsWith(".docx"))
            return "docx";
        if (lower.endsWith(".doc"))
            return "doc";
        if (lower.endsWith(".txt"))
            return "txt";
        if (lower.endsWith(".md"))
            return "md";
        throw new BadRequestException(
            "不支持的文件类型（如 PNG、JPG 等图片格式，以及扫描版 PDF）。"
            + "当前仅支持文本型文档：PDF、DOCX、DOC、TXT、MD");
    }

    /**
     * 检查文件大小是否超出限制
     *
     * @param fileSize 文件大小（字节）
     * @param maxSize  最大允许大小（字节）
     * @throws BadRequestException 如果文件过大
     */
    public static void checkFileSize(long fileSize, long maxSize) {
        if (fileSize > maxSize) {
            throw new BadRequestException("文件过大，最大支持 " + (maxSize / 1024 / 1024) + "MB");
        }
        if (fileSize <= 0) {
            throw new BadRequestException("文件为空，请上传有效文件");
        }
    }
}
