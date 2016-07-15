package edu.msu.mi.forum.model

/**
 * Created by josh on 7/14/16.
 */
interface Post {

    public DiscussionThread getThread()

    public Date getTime()

    public String getPoster()

    public String getContent()

    public String getId()

    public String getReplyToId()

}