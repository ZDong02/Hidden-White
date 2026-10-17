package com.hiddenwhite.admin.generator.controller;

import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.framework.audit.OperLog;
import com.hiddenwhite.framework.security.RequiresPermission;
import com.hiddenwhite.admin.generator.service.GeneratorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 代码生成器。
 *
 * <p><b>权限码按读写分离</b>：此前 8 个接口全部共用 {@code tool:gen:list}，
 * 意味着拿到「只读」权限的账号也能导入表、修改配置并生成下载代码——
 * 而生成代码会向磁盘写入文件。拆分后：
 * <ul>
 *   <li>{@code tool:gen:list} —— 查表、查列、预览（只读）</li>
 *   <li>{@code tool:gen:edit} —— 导入表、修改生成配置（写入 gen_table）</li>
 *   <li>{@code tool:gen:code} —— 生成并下载代码（写文件）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/tool/generator")
public class GeneratorController {

    private final GeneratorService service;

    public GeneratorController(GeneratorService service) {
        this.service = service;
    }

    /** 可导入表列表（只读）。 */
    @GetMapping("/tables")
    @RequiresPermission("tool:gen:list")
    public R<PageResult<Map<String, Object>>> tables(PageQuery query, @RequestParam(required = false) String keyword) {
        return R.success(service.tables(query, keyword));
    }

    /** 待导入的表清单（只读）。 */
    @GetMapping("/importable-tables")
    @RequiresPermission("tool:gen:list")
    public R<List<Map<String, Object>>> importableTables(@RequestParam(required = false) String keyword) {
        return R.success(service.importableTables(keyword));
    }

    /** 导入表到 gen_table（写库）。 */
    @PostMapping("/import")
    @RequiresPermission("tool:gen:edit")
    @OperLog(module = "代码生成", action = "导入表")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> importTables(@RequestBody Map<String, Object> request) {
        return R.success(service.importTables((List<String>) request.get("tableNames")));
    }

    /** 表字段（只读）。 */
    @GetMapping("/tables/{id}/columns")
    @RequiresPermission("tool:gen:list")
    public R<List<Map<String, Object>>> columns(@PathVariable Long id) {
        return R.success(service.columns(id));
    }

    /** 生成结果预览（只读，不落盘）。 */
    @GetMapping("/tables/{id}/preview")
    @RequiresPermission("tool:gen:list")
    public R<Map<String, Object>> preview(@PathVariable Long id) {
        return R.success(service.preview(id));
    }

    /** 修改生成配置（写库）。 */
    @PutMapping("/tables/{id}")
    @RequiresPermission("tool:gen:edit")
    @OperLog(module = "代码生成", action = "修改配置")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        service.updateTable(id, request);
        return R.success(null);
    }

    /** 生成代码（写文件）。 */
    @PostMapping("/tables/{id}/generate")
    @RequiresPermission("tool:gen:code")
    @OperLog(module = "代码生成", action = "生成代码")
    public R<Map<String, Object>> generate(@PathVariable Long id) {
        return R.success(service.generate(id));
    }

    /** 打包下载（写文件）。 */
    @GetMapping("/tables/{id}/download")
    @RequiresPermission("tool:gen:code")
    @OperLog(module = "代码生成", action = "下载代码")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Map<String, Object> preview = service.preview(id);
        String activeFile = String.valueOf(preview.get("activeFile"));
        String className = activeFile.endsWith("Controller.java")
                ? activeFile.substring(0, activeFile.length() - "Controller.java".length())
                : "code";
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + className + ".zip\"")
                .body(service.download(id));
    }
}
