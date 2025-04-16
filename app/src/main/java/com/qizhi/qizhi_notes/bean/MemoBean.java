package com.qizhi.qizhi_notes.bean;

// Plain Old Java Object (POJO) for a Memo/Note
public class MemoBean {
    private int id;
    private String title;
    private String content;
    private String imgPath; // Stores URI string or file path
    private String createTime;

    // Default constructor
    public MemoBean() {
    }

    // Constructor with fields (optional)
    public MemoBean(int id, String title, String content, String imgPath, String createTime) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.imgPath = imgPath;
        this.createTime = createTime;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImgPath() {
        return imgPath;
    }

    public void setImgPath(String imgPath) {
        this.imgPath = imgPath;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "MemoBean{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", content='" + (content != null ? content.substring(0, Math.min(content.length(), 20)) + "..." : "null") + '\'' + // Avoid logging large content
                ", imgPath='" + imgPath + '\'' +
                ", createTime='" + createTime + '\'' +
                '}';
    }
}