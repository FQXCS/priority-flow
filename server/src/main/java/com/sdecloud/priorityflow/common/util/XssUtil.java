package com.sdecloud.priorityflow.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * 富文本 XSS 清洗：白名单标签后入库。
 *
 * @author priority-flow
 */
public final class XssUtil {

    private XssUtil() {
    }

    /**
     * 清洗富文本：保留常用排版标签，剔除脚本/事件属性/危险协议
     */
    public static String cleanRichText(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        // relaxed 白名单：a/b/i/blockquote/p/ul/ol/li/table 等，附加 img 与删除线
        return Jsoup.clean(html, "", Safelist.relaxed()
                .addTags("s", "u", "span", "h1", "h2", "h3")
                .addAttributes(":all", "style", "class")
                .addProtocols("a", "href", "http", "https", "mailto"));
    }

    /**
     * 清洗纯文本输入（剔除所有标签）
     */
    public static String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return Jsoup.clean(text, "", Safelist.none());
    }
}
