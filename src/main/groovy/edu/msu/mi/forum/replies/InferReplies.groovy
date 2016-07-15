package edu.msu.mi.forum.replies

import edu.msu.mi.forum.model.Conversation
import edu.msu.mi.forum.model.Corpus
import edu.msu.mi.forum.model.DiscussionThread
import edu.msu.mi.forum.model.Post
import edu.msu.mi.forum.util.GroovyUtils
import edu.msu.mi.forum.util.WordChecker
import groovy.util.logging.Log4j

import java.util.regex.Matcher

import static edu.msu.mi.forum.util.WordChecker.Type.DRUG
import static edu.msu.mi.forum.util.WordChecker.Type.MISSPELLING
import static edu.msu.mi.forum.util.WordChecker.Type.WORD


@Log4j
/**
 * Infer replies in a forum where people refer to one another by name
 */
class InferReplies {

    Corpus corpus
    Conversation conversation
    Set posters
    Map nameToHandle = [:]
    Map handleToName = [:]
    boolean processRollCall = false


    public InferReplies(Corpus corpus, boolean processRollCall = false) {
        this.corpus = corpus
        this.processRollCall = processRollCall
    }

    public Conversation getConversation() {
        conversation ?: (conversation = corpus.getLongConversation())

    }

    public Set getPosters() {
        posters ?: (posters = corpus.posters)

    }

    /**
     * Processes the corpus, and returns a map of post ids to sets of names (posters)
     *
     * @param endDate The latest date that should be processed
     * @return A map of postid-> [Set of poster names]
     *
     */
    public Map process(Date endDate = null) {

        Map result = [:]

        //Find anything that looks like a reference
        extractReferences()


        //go through each thread and attempt to resolve replies based on references
        getConversation().allThreads.collectEntries() { DiscussionThread thread ->
            if (!endDate || thread.posts[0].time < endDate) {
                result << processThread(thread, endDate)
            } else [:]
        }


        //recapitalize everything as necessary
        Map recap = getPosters().collectEntries() { [it.toLowerCase(), it] }

        result = result.collectEntries { k, v ->
            [k, v.collect { recap[it] } as Set]
        }
        result


    }

    /**
     * Extract all potentials references for each user using several methods:
     * <ul>
     *     <li> @ref signatureExtractionBySelfReference</li>
     *     <li> @ref signatureExtractionBySubstring</li>
     *     <li> @ref signatureExtractionByFrequentClosing</li>
     *     <li> @ref signatureExtractionByReply</li>
     * </ul>
     *
     * Return a map of handle ('official' usernme) -> [Set of possible references]
     *
     */
    public Map extractReferences() {


        List<DiscussionThread> rollCallAThreads = []
        List<DiscussionThread> threads = [] + getConversation().allThreads
        threads.findAll {
            if (it.subject.toLowerCase().contains("roll call")) {
                rollCallAThreads << it
                return true
            } else {
                return false
            }
        }
        handleToName = GroovyUtils.sumCollectedItems(signatureExtractionBySelfReference(threads),
                signatureExtractionByReply(), signatureExtractionBySubstring(), signatureExtractionByFrequentClosing())

        handleToName = handleToName.collectEntries { String k, Set v ->
            [k.toLowerCase(), (v.collect { it.toLowerCase() } as Set)]
        }

        handleToName.each { k, v -> v.each { GroovyUtils.collectItems(nameToHandle, it, k) } }
        handleToName
    }


    /**
     * Extract signatures based on self-identification
     *
     *
     * @return A map of hndles ('official' username)-> [Set of possible references]
     */

    public Map signatureExtractionBySelfReference(List<DiscussionThread> threads) {
        Map result = [:]
        threads.each { DiscussionThread thread ->
            thread.posts.each { post ->
                String text = cleanPostText(post.content)
                Set partial = rollCallBodyTemplates.sum { pattern ->
                    text.findAll(pattern) { match, String first ->
                        first ?: null
                    } as Set
                }
                GroovyUtils.collectItems(result, post.poster, partial)
            }
        }
        return result

    }


    /**
     * Extract signatures based on lexical manipulation of the username
     *
     *
     * @return A map of handles ('official' username)-> [Set of possible references]
     */
    public signatureExtractionBySubstring() {

        posters.each { p ->

            Set consecutive = p.findAll(/[A-Za-z]+/) as Set
            if (consecutive) {
                handleToName[p] = ((handleToName[p] ?: []) + consecutive) as Set
                Set chunks = consecutive.sum() {
                    it.findAll(/[A-Z][a-z]+/) as Set
                }

                if (chunks) {

                    handleToName[p] = ((handleToName[p] ?: []) + chunks) as Set
                }
            }
        }
        handleToName
    }


    /**
     * Extract signatures based on frequent closings by each user
     *
     *
     * @return A map of handles ('official' username)-> [Set of possible references]
     */
    public Map signatureExtractionByFrequentClosing() {
        String poster = null
        Map candidates = [:]
        List names = []
        Map counts = [:]
        Closure compressList = { list ->
            Map result = [:]
            list.each {
                result[it] = (result[it] ?: 0) + 1
            }
            return result
        }

        corpus.postsByAuthor.each { Post post ->
            if (post.poster != poster && counts[poster]) {
                Map m = compressList(names)
                if (m) {
                    Map current = candidates[poster] ?: (candidates[poster] = [:])
                    m.each { k, v ->
                        if (current[k]) {
                            current[k] += v
                        } else {
                            current[k] = v
                        }
                    }
                }

                names.clear()
                counts[post.poster] = 0
            }
            poster = post.poster
            counts[poster] = (counts[poster] ?: 0) + 1
            String text = cleanPostText(post.content)
            List sentences = (text.split(/[\?!\.]/) as List)
            names += (sentences.findResults {
                String s = sentences.last()
                List<String> lastSentence = s.findAll(/\w+/)
                (lastSentence && lastSentence.last().matches(/[A-Z].*/) && !lastSentence.first().matches(/[Tt]hank/) &&
                        (!(WordChecker.lookup(lastSentence.last()) in [WORD, DRUG, MISSPELLING])
                                || WordChecker.isProperName(lastSentence.last()))) ? lastSentence.last() : null
            } as Set)


        }

        candidates = candidates.entrySet().collectEntries { ent ->
            [ent.key, (((Map) ent.value).entrySet() as List).sort {
                -it.value
            }.findResults {

                //note, this is entirely ad hoc - a good target for improvement!
                (counts[ent.key] <= 10 && it.value >= 2) || (it.value / (double) counts[ent.key]) > 0.25 ? it.key : null
            } as Set]

        }


    }


    /**
     * Extract signatures based on how following posters address their posts
     *
     *
     * @return A map of handles ('official' username)-> [Set of possible references]
     */
    public Map signatureExtractionByReply() {
        Map result = [:]
        Map occurrences = [:]
        String topposter


        def tp = { Post p -> p.thread.firstPost.poster }

        def newPoster = { Post p ->
            tp(p) != topposter
        }

        def account = {
            if (occurrences) {
                GroovyUtils.collectItems(result, topposter, occurrences.max { Map.Entry kv ->
                    kv.value
                }.key)
            }
            occurrences.clear()
        }

        corpus.repliesByFirstPoster.each { Post p ->
            String text = cleanPostText(p.content)
            if (newPoster(p)) {
                if (topposter != null) account()
                topposter = tp(p)
            }

            Set names = checkedResponseTemplates.findResults { String pattern, Closure fx ->
                fx(text =~ pattern)
            } as Set

            GroovyUtils.countOccurrences(occurrences, names)
        }

        //this only picks up the most frequent matching word
        account()
        result
    }


    /**
     * "Roll call" threads occasionally show up in moderated forums, and these are great places
     * to figure out what people call themselves.  This method is not called, but it remains here for
     * reference
     *
     * @param threads
     * @return
     */
    public Map processRollCallThreads(List threads) {
        threads.collectEntries { DiscussionThread thread ->
            thread.posts.collectEntries { post ->
                String text = cleanPostText(post.content)
                rollCallIntroTemplates.collect { pattern ->
                    String firstSentence = text.split(/(?<!\d)[\.!?]/)[0]
                    firstSentence.findAll(pattern) { match, first ->
                        if (first) {

                            first
                        } else {
                            null
                        }

                    }.flatten() - null
                }
            }

        }
    }


    public void registerReferenceForHandle(String ref, String handle) {
        GroovyUtils.collectItems(nameToHandle, ref.toLowerCase(), handle.toLowerCase())
        GroovyUtils.collectItems(handleToName, handle.toLowerCase(), ref.toLowerCase())

    }



    /**
     * Returns all of the possible names that might be used to refer to the person
     * whose handle this is
     *
     * @param userid
     * @return
     */
    public Set resolveHandle(String userid) {
        ((handleToName[userid] ?: []) as Set) + [userid]
    }

    /**
     * Returns all of the possible names that might be used to refer to the person
     * whose handle this is
     *
     * @param userid
     * @return
     */
    public Set resolveReference(String name) {
        ((name in getPosters()) ? [name] : nameToHandle[name]) as Set
    }

    /**
     * Process a given discussion thread, attempting to resolve the intended target of each reply in a thread
     *
     * @param thread The thread to be processed
     * @param end The last date to process
     * @return A map of post ids -> [Set of likely targets]
     */
    public Map processThread(DiscussionThread thread, Date end = null) {

        List recents = []
        Map result = [:]
        Map postmap = [:]
        thread.posts.clone().find { Post post ->

            String userid = post.poster.toLowerCase()
            postmap[post.id] = userid

            (end && (post.time > end)) ?: {
                String text = cleanPostText(post.content).toLowerCase()
                Set candidates = (text.findAll(/\w+/).findResults { word ->
                    matchWord(userid, recents, word)
                } as Set)


                candidates += findReplyPatterns(recents, post, thread)

                if (candidates) {
                    result[post.id] = candidates
                }
                //only add a reply to id if we haven't found other candidates
                if (!result[post.id] && post.replyToId && postmap[post.replyToId] != userid) {
                    result[post.id] = (result[post.id] ?: [] as Set) + postmap[post.replyToId]
                }

                recents = [userid] + (recents - userid)

                return false
            }()

        }


        result
    }

    /**
     * Find the most likely targets of a given post
     *
     * @param recents A list of posters, in order of oldest poster first
     * @param post The post to examine
     * @return A set of likely targets
     */
    public Set findReplyPatterns(List recents, Post post, DiscussionThread thread) {
        Set result = checkedResponseTemplates.entrySet().sum { Map.Entry ent ->
            String pattern = ent.getKey()
            Closure function = ent.getValue()
            String match = function(post.content =~ pattern)?.toLowerCase()

            def isTop = { Post p -> p.thread.firstPost == p }


            if (match) {
                if (!matchWord(post.poster, recents, match)) {
                    //Set handle = resolveReference(match)
                    if (isTop(post)) {
                        //No way to resolve; dump
                        log.debug("Cannot resolve ${match} for ${post.id} (top level)")
                        return []
                    } else {
                        //for all posts that are before this one and authored by someone else, find the closest one
                        //that contains the identified token
                        //if not found, default to top post


                        List posts = thread.posts.findAll {
                            it.time < post.time && it.poster != post.poster
                        }.reverse()


                        def max = [len: 0, post: null]
                        Post pmatch = posts.find { Post p ->
                            int i = longestSubstr(match.toLowerCase(), p.poster.toLowerCase())
                            if (i > max.len) {
                                max.len = i
                                max.post = p
                            }

                            p.content.toLowerCase().find("[\\s,]${match}(?:\$|[\\s,!?\\.\\-;:])")

                        } ?: (max.len >= 3 && max.post.poster != post.poster) ? max.post : null


                        if (pmatch) {

                            registerReferenceForHandle(match, pmatch.poster)
                            return [pmatch.poster]
                        } else {
                            println "Cannot resolve ${match} for ${post.poster} - ${max.post ? "best guess is ${max.post.poster}:${max.len}" : "no guess"}"
                            return []
                        }
                    }


                }
            }
            return []
        } as Set

        return result
    }


    public String matchWord(String author, List seen, String word) {
        author = author.toLowerCase()
        word = word.toLowerCase()
        seen.find() {
            it != author && resolveHandle(it).contains(word)
        }

    }




    public static int longestSubstr(String first, String second) {
        if (first == null || second == null || first.length() == 0 || second.length() == 0) {
            return 0;
        }

        int maxLen = 0;
        int fl = first.length();
        int sl = second.length();
        int[][] table = new int[fl + 1][sl + 1];

        for (int s = 0; s <= sl; s++)
            table[0][s] = 0;
        for (int f = 0; f <= fl; f++)
            table[f][0] = 0;




        for (int i = 1; i <= fl; i++) {
            for (int j = 1; j <= sl; j++) {
                if (first.charAt(i - 1) == second.charAt(j - 1)) {
                    if (i == 1 || j == 1) {
                        table[i][j] = 1;
                    } else {
                        table[i][j] = table[i - 1][j - 1] + 1;
                    }
                    if (table[i][j] > maxLen) {
                        maxLen = table[i][j];
                    }
                }
            }
        }
        return maxLen;
    }

//NOTE matched name may need to be split with "/" or "'"
/**
 /(?!I'm|I am|[Ww]ho is|still)([A-Z]\w+)\s(?:here|present)(?:[!\.])/,
 /([\w\/\\]+)\.\.+\s(?:sort of|kind of|not really)?(?:here|present)/,
 /([A-Z][\w\/\\]+)\s(?:fm|from)\s*[A-Z]\w+/,
 /1[\.\)]\s?(\w+)/
 **/
    private static List<String> rollCallIntroTemplates = [
            /^(?!Still)([A-Z]\w+)\s(?:here|present)/,
            /1[\.\)]\s?(\w+)[\s\.]+2/


    ]

    private static List<String> rollCallBodyTemplates = [
            /[Hh]i,?\s(\w+) here/,
            /[Mm]y name is (\w+)/,
            /[Jj]ust call me (\w+)/,
            /[eE]veryone calls me (\w+)/,
            /[Yy]ou can call me (\w+)/,
            /I go by (?:the name of)?\s((?:Mr\. )?\w+)/,
            /Name\s?[-:]\s?(\w+)/

    ]


    private static Map<String, Closure> uncheckedResponseTemplates = [
            (/(?:Hi|Hey|Greetings|Afternoon|Morning|Hello|Dear),?\s*(\w+)[\.!,?]?\s+(?!here|from\s+\w+\s+here).+/): { Matcher m, String author = null, List seen = null ->
//                println "$m"
//                if (m.matches()) {
//                    println "Matcher matches"
//                }
                m.matches() ? [m[0][1]] : null
            }
    ]

    private static Map<String, Closure> checkedResponseTemplates = [
            (/(?:Hi|Hey|Greetings|Afternoon|Morning|Hello|Dear)[,;\s]\s*(\w+)[\.!,?]?\s+(?!here|from\s+\w+\s+here).+/): { Matcher m, String author = null, List seen = null ->
//                println "$m"
//                if (m.matches()) {
//                    println "Matcher matches"
//                }
                (m.matches() && !(WordChecker.lookup(m[0][1]) in [WORD, DRUG, MISSPELLING])) ? {
                    author ? matchWord(author, seen, m[0][1]) : m[0][1]

                }() : null
            }
    ]

    private static cleanPostText = { String txt -> txt.replaceAll(/<APO>/, "'") }

}