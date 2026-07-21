package com.Myself.demo.util;

import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;

import java.util.List;

public class MediaExtractor {

    private final WeixinMessage message;

    public MediaExtractor(WeixinMessage msg) {
        this.message = msg;
    }

    public boolean hasImage() { return findImage() != null; }
    public boolean hasVoice() { return findVoice() != null; }
    public boolean hasFile()  { return findFile()  != null; }
    public boolean hasText()  { return findText()  != null; }

    public MessageItem getImage() { return findImage(); }
    public MessageItem getVoice() { return findVoice(); }
    public MessageItem getFile()  { return findFile();  }
    public String getText() {
        MessageItem item = findText();
        return item != null ? item.getText_item().getText() : null;
    }

    public String getFromUserId() { return message.getFrom_user_id(); }
    public List<MessageItem> getItems() { return message.getItem_list(); }

    private MessageItem findImage() {
        for (MessageItem item : items())
            if (item.getImage_item() != null) return item;
        return null;
    }

    private MessageItem findVoice() {
        for (MessageItem item : items())
            if (item.getVoice_item() != null) return item;
        return null;
    }

    private MessageItem findFile() {
        for (MessageItem item : items())
            if (item.getFile_item() != null) return item;
        return null;
    }

    private MessageItem findText() {
        for (MessageItem item : items())
            if (item.getText_item() != null) return item;
        return null;
    }

    private List<MessageItem> items() {
        return message.getItem_list() != null ? message.getItem_list() : java.util.Collections.emptyList();
    }
}
