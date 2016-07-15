package edu.msu.mi.forum.util

import org.atteo.evo.inflector.English

/**
 * Created by josh on 4/18/15.
 */
class WordChecker {


    public static final Set words = new HashSet<String>(300000)
    public static final Set names = new HashSet<String>(1308)
    public static final Set drugs = new HashSet<String>(12000)
    public static final Set misspellings = new HashSet<String>(30)


    static enum Type {
        NAME,WORD,DRUG,MISSPELLING,UNKNOWN
    }

    static {


        WordChecker.class.getClassLoader().getResourceAsStream("propernames").eachLine {
            String w = it.trim().toLowerCase()
            names<<w
        }

        WordChecker.class.getClassLoader().getResourceAsStream("wordsEn.txt").eachLine {

            String w = it.trim().toLowerCase()
            if (w[0].matches(/[a-z]/)) {

                words<<w
                String w2 = English.plural(w)
                if (w2) {
                    words<<w2
                }

            }
        }

        WordChecker.class.getClassLoader().getResourceAsStream("drugs.txt").eachLine {
            String w = it.trim().toLowerCase()
            drugs<<w
        }

        WordChecker.class.getClassLoader().getResourceAsStream("misspellings.txt").eachLine {
            String w = it.trim().toLowerCase()
            misspellings<<w
        }

    }

    static Type lookup(String s) {
        s = s.toLowerCase()
        words.contains(s)?Type.WORD:
                drugs.contains(s)?Type.DRUG:
                        names.contains(s)?Type.NAME:
                                misspellings.contains(s)?Type.MISSPELLING:
                                        Type.UNKNOWN
    }

    static boolean isProperName(String s) {
        names.contains(s.toLowerCase())
    }




}
