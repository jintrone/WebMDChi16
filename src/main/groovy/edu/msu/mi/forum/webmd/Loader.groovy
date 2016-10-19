package edu.msu.mi.forum.webmd

import edu.msu.mi.forum.model.Conversation
import groovy.sql.Sql
import groovy.time.TimeCategory


/**
 * Created by josh on 4/2/15.
 */

class Loader {


    static ConfigObject props;

    static Map COLS = [(WebMdConversation.Column.ID)  : "uniqueID", (WebMdConversation.Column.THREAD): "qid", (WebMdConversation.Column.CREATED_TS): "date", (WebMdConversation.Column.AUTHOR): "poster",
                       (WebMdConversation.Column.TEXT): "content", (WebMdConversation.Column.REPLY): "replyTo", (WebMdConversation.Column.SUBJECT): "title"]

    private static Loader instance;


    private Loader() {


        URL propsFile = getClass().getClassLoader().getResource("webmd_analysis.${System.getenv()['USER']}.groovy")
        println propsFile
        props = new ConfigSlurper().parse(propsFile)
        instance = this
    }

    public static Loader getInstance() {
        instance ?: new Loader()

    }


    List<Corpus> getCorpora() {
        getSqlConnection().rows("select * from all_corpora").collect {
            new Corpus(it.table_name)
        }
    }

    public Corpus getCorpus(String name) {
        getCorpora().collectEntries {
            [it.name, it]
        }[name] ?: null
    }


    public static Sql getSqlConnection() {
        Sql.newInstance("jdbc:mysql://${props.host}:${props.port}/${props.database}", props.username, props.password, 'com.mysql.jdbc.Driver')
    }


    public static class Corpus {

        String table
        String name

        String longQuery = "select uniqueID, qid, date, poster, title, replyTo, content from ${table}" as String
        String shortQuery = "select uniqueID, qid, date, poster, title, replyTo, '' as content from ${table}" as String
        String authorQuery = "select distinct poster from ${table}" as String
        String postsByAuthor = "select poster, title, content from ${table} order by poster" as String
        String postsByResponder = "select b.poster topposter, a.qid, a.date, a.poster respondant, a.content, a.`uniqueID` from ${table} a inner join (select poster,`qid` from ${table} where localID=-1 order by poster) b on (a.qid=b.qid) where a.localID > -1 and a.poster<>b.poster and right(a.`replyTo`,4)='_top' order by topposter, a.qid, a.localID" as String
        String postsByNamedAuthors = "select count(*) count from ${table} where poster in " as String
        String postCount = "select count(*) count from ${table}" as String


        private Conversation conversation


        public Corpus(String table) {

            this.table = table
            this.name = table
        }


        public Conversation getConversation() {
            conversation ?: {
                conversation = new WebMdConversation(name, getSqlConnection(), shortQuery, COLS)
            }()
        }

        public Conversation getLongConversation() {
            conversation ?: {
                conversation = new WebMdConversation(name, getSqlConnection(), longQuery, COLS)
            }()
        }

        public List<String> getPosters() {
            getSqlConnection().rows(authorQuery).collect {
                it.poster

            }
        }

        public int getNumberOfPosts() {
            getSqlConnection().firstRow(postCount).count as int
        }

        public int getNumberOfPostsBy(Collection names) {
            def values = "'${names.join('\',\'')}'"
            def query = "${postsByNamedAuthors}($values)" as String

            getSqlConnection().firstRow(query).count as int
        }

        public Date getLastPostTime() {
            getSqlConnection().firstRow("select max(date) d from ${table}" as String).d as Date
        }

        public Date getFirstPostTime() {
            getSqlConnection().firstRow("select min(date) d from ${table}" as String).d as Date
        }

        public Date[] getIntervals(int days) {
            List<Date> result = []
            Date lastPostTime = getLastPostTime()
            Date firstPostTime = getFirstPostTime()
            while (!result || result.last() < lastPostTime) {
                use(TimeCategory) {
                    result << (result ? result.last() : firstPostTime) + (days).day
                }
            }
            result as Date[]
        }


        public String toString() {
            name
        }
    }

    public static boolean checkIfInferredReplyColumnExists(Corpus corpus) {
        Sql connection = Loader.sqlConnection
        String q = """
            SELECT *
                FROM information_schema.COLUMNS
            WHERE
            TABLE_SCHEMA = '${Loader.props.database}'
            AND TABLE_NAME = '${corpus.table}'
            AND COLUMN_NAME = 'inferred_replies'
        """ as String
        connection.rows(q).size() > 0
    }


    public static void addInferredReplyToTableSchema(Corpus corpus) {

        if (!checkIfInferredReplyColumnExists(corpus)) {
            String alter = "alter table `${corpus.table}` add column inferred_replies varchar(1024)"
            Loader.sqlConnection.execute(alter)
        }
    }

    public static void addInferredReplies(Map replies, Corpus corpus) {
        Sql connection = Loader.sqlConnection
        connection.connection.autoCommit = false
        String update = """update ${corpus.table}
                        set inferred_replies = :newvalue
                        where uniqueID = :key """
        connection.withBatch(20, update) { ps ->
            replies.each { String id, Set names ->
                names -= null
                if (names) {
                    ps.addBatch(key: id, newvalue: (names.join(",")))
                }

            }
        }
        connection.commit()
    }


    public static void test() {
        println Loader.instance.corpora.collect { it.name }
        Corpus c = Loader.instance.corpora[0]
        println c.posters
        println c.longConversation.allThreads[0].firstPost.content

    }


    public static void main(String[] args) {
        test()
    }


}


