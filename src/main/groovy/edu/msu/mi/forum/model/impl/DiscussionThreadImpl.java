package edu.msu.mi.forum.model.impl;



import edu.msu.mi.forum.model.DiscussionThread;
import edu.msu.mi.forum.model.Post;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
* Created by josh on 1/20/14.
*/
public class DiscussionThreadImpl implements DiscussionThread {

    private String threadid;
    private String subject;
    private List<Post> posts = new ArrayList<Post>();
    private boolean sorted = false;



    public DiscussionThreadImpl(String threadid, String subject) {
       this.threadid = threadid;
        this.subject = subject;
    }


    public String getSubject() {
        return subject;
    }

    public String getThreadId() {
       return threadid;
    }

    public void addPost(Post p) {
        ((PostImpl)p).setThread(this);
        this.posts.add(p);
        sorted = false;
    }

    public List<Post> getPosts() {
        if (!sorted) {
            Collections.sort(posts, new Comparator<Post>() {
                public int compare(Post post, Post post1) {
                    return post.getTime().compareTo(post1.getTime());
                }
            });
        }
        return posts;
    }

    @Override
    public Post getFirstPost() {
        return getPosts().size()>0?getPosts().get(0):null;
    }


}
