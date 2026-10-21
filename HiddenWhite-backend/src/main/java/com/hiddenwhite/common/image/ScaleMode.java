package com.hiddenwhite.common.image;

/**
 * 图片缩放模式。
 *
 * <p>两种模式都保证「不变形」：输出宽高比始终等于目标宽高比。
 * <ul>
 *   <li>{@link #COVER} 居中裁剪：铺满目标框，超出部分从中心裁掉，不留白边；</li>
 *   <li>{@link #CONTAIN} 等比缩放：完整保留原图，短边贴合目标框，剩余区域以背景色填充。</li>
 * </ul>
 */
public enum ScaleMode {

    /** 居中裁剪：铺满目标框，不留白。 */
    COVER,

    /** 等比缩放：完整保留原图，空白处以背景色填充。 */
    CONTAIN
}
