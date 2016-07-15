package edu.msu.mi.forum.model

/**
 * Created by josh on 7/14/16.
 */
interface DiscussionThread {

    public List<Post> getPosts();

    public Post getFirstPost()

    public String getSubject()
}
