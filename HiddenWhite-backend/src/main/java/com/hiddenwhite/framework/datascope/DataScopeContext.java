package com.hiddenwhite.framework.datascope;

public final class DataScopeContext {

    private static final ThreadLocal<DataScopeCriteria> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(DataScopeCriteria criteria) {
        HOLDER.set(criteria == null ? DataScopeCriteria.selfOnly(null) : criteria);
    }

    /**
     * 取当前数据范围。
     *
     * <p><b>无上下文时返回最严的 selfOnly(null)，而不是全量 all()</b>：
     * 新增查询若忘记标注 {@code @DataScope}，应当「查不到数据」并被测试发现，
     * 而不是静默返回全表。这是有意的 fail-closed 设计，不要改回 all()。
     */
    public static DataScopeCriteria current() {
        DataScopeCriteria criteria = HOLDER.get();
        return criteria == null ? DataScopeCriteria.selfOnly(null) : criteria;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
