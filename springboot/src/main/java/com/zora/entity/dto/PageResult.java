package com.zora.entity.dto;

import java.util.List;

/**
 * 通用分页结果 DTO
 * <p>替代 Service 层返回 {@code Map<String, Object>} 的分页模式，
 * 提供类型安全的分页元数据（total/page/size）。</p>
 *
 * <p>JSON 序列化后格式: {@code { "list": [...], "total": 100, "page": 1, "size": 20 }}</p>
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int page;
    private int size;

    public PageResult() {}

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
