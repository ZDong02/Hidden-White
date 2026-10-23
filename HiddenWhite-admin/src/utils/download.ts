/**
 * 触发浏览器下载。
 *
 * <p>对象 URL 不能紧接着 {@code click()} 同步释放：下载是异步启动的，
 * 部分浏览器（Firefox / 旧版 Safari）在 click 返回后才去读 blob，
 * 同步 revoke 会让下载悄悄失败——界面还提示「已导出」，文件却没有。
 * 这里改成下一轮事件循环再释放。
 */
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
