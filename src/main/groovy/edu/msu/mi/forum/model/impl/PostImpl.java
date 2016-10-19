package edu.msu.mi.forum.model.impl;



import edu.msu.mi.forum.model.DiscussionThread;
import edu.msu.mi.forum.model.Post;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
* Created by josh on 1/20/14.
*/
public class PostImpl implements Post {

    public String postId,replyToId,threadId,posterId,content;
    public DiscussionThread thread;
    public Date creation;



    public PostImpl(String postId, String replyToId, String userId, Date creation) {

        this.postId = postId;
        this.replyToId = replyToId;
        this.posterId = userId;
        this.creation = creation;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setCreation(Date creation) {
        this.creation = creation;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public void setReplyToId(String replyToId) {
        this.replyToId = replyToId;
    }


    public void setThread(DiscussionThread thread) {
        this.thread = thread;
    }
    public DiscussionThread getThread() {return thread;}


    public String getId() {
        return postId;
    }

    public String getReplyToId() {
       return replyToId;
    }


    public void setPoster(String userId) {
        this.posterId = userId;
    }

    public String getPoster() {
       return posterId;
    }

    public Date getTime() {
        return creation;
    }



    public String getContent() {
        return content;
    }
}
