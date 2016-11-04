package edu.msu.mi.forum.model

/**
 * Created by josh on 7/14/16.
 */
interface Corpus {

    /**
     * Return the conversation (a wrapper around a set of threads)
     *
     * @return
     */
    public Conversation getConversation()

    /**
     * Returns a list of all posts in the corpus ordered by author
     *
     * @return
     */
    public Iterable<Post> getPostsByAuthor();

    /**
     * Return a list of replies sorted by top poster in the thread - note that the
     * resulting list does not contain all posts - only replies
     * @return
     */
    public Iterable<Post> getRepliesByFirstPoster();

    public Set<String> getPosters()

}