package edu.msu.mi.forum.webmd

import edu.msu.mi.forum.model.DiscussionThread
import edu.msu.mi.forum.model.Post
import edu.msu.mi.forum.model.impl.ConversationImpl
import edu.msu.mi.forum.model.impl.DiscussionThreadImpl
import edu.msu.mi.forum.model.impl.PostImpl
import groovy.sql.Sql

/**
 * Created by josh on 1/20/14.
 */
class WebMdConversation extends ConversationImpl {

    public static enum Column {
        ID, REPLY, CREATED_TS, CREATED_S, AUTHOR, TEXT, THREAD, SUBJECT
    }

    /**
     *
     * @param corpusName
     * @param s
     * @param query Query should guarantee that the rows are in order
     * @param cols
     */
    public WebMdConversation(String name, Sql s, String query, Map cols) {
        super(name)
        read(s, query, cols)
    }

    def read(Sql s, String query, Map cols) {

        Map<String, DiscussionThread> threads = [:]
        s.eachRow(query) { it ->

            def d = cols[Column.CREATED_TS] ? new Date(((java.sql.Timestamp) it[cols[Column.CREATED_TS]]).getTime()) : new Date((it[cols[Column.CREATED_S]] as Long) * 1000l)
            Post p = new PostImpl(it[cols[Column.ID]] as String, null, it[cols[Column.AUTHOR]].trim(), d)
            p.setContent(it[cols[Column.TEXT]])
            String thread = it[cols[Column.THREAD]] as String;
           if (!threads[thread]) {
                threads[thread] = new DiscussionThreadImpl(thread,it[cols[Column.SUBJECT]])
            }
            p.replyToId = it[cols[Column.REPLY]]
            threads[thread].addPost(p)


        }
        threads.each { k, v ->
            addThread(v)
        }


    }
}