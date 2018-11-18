package com.o19s.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.search.vectorhighlight.*;
import org.apache.lucene.store.Directory;
import org.junit.Test;

import java.io.FileWriter;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;


/**
 * Unit test for simple App.
 */
public class HighlighterTest {

    /**
     * Exercise the highlighter API without any index!
     * See Lucene in Action, 2nd edition, pages 272-273
     */
    @Test
    public void runHighlighter() {
        try {
            final String text =
                    "In this section we'll show you how to make the simplest " +
                            "programmatic query, searching for a single term, and then " +
                            "we'll see how to use QueryParser to accept textual queries. " +
                            "In the sections that follow, we’ll take this simple example " +
                            "further by detailing all the query types built into Lucene. " +
                            "We begin with the simplest search of all: searching for all " +
                            "documents that contain a single term.";

            String searchText = "single term";
            QueryParser parser = new QueryParser("f", new StandardAnalyzer());
            Query query = parser.parse(searchText);

            SimpleHTMLFormatter formatter =
                    new SimpleHTMLFormatter("<span class=\"highlight\">",
                            "</span>");
            TokenStream tokens = new StandardAnalyzer()
                    .tokenStream("f", new StringReader(text));
            QueryScorer scorer = new QueryScorer(query, "f");
            Highlighter highlighter
                    = new Highlighter(formatter, scorer);
            highlighter.setTextFragmenter(
                    new SimpleSpanFragmenter(scorer));
            String result =
                    highlighter.getBestFragments(tokens, text, 3, "...");
            System.out.println(String.format("Best fragments: %s", result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create a RAM-based index;
     * Index some documents;
     * Perform a search;
     * And highlight the result.
     * <p>
     * Source: Example code in https://lucene.apache.org/core/7_5_0/core/overview-summary.html
     */
    @Test
    public void indexSearchAndHighlightResults() {
        try {
            Analyzer analyzer = new StandardAnalyzer();

            final String[] DOCS = {
                    "This is the text to be indexed."
            };

            Directory directory = TestUtils.index("title", DOCS, analyzer);

            // Now search the index:
            DirectoryReader ireader = DirectoryReader.open(directory);
            IndexSearcher isearcher = new IndexSearcher(ireader);
            // Parse a simple query that searches for "text":
            QueryParser parser = new QueryParser("title", analyzer);
            Query query = parser.parse("text");
            ScoreDoc[] hits = isearcher.search(query, 10, Sort.RELEVANCE).scoreDocs;
            assertEquals(1, hits.length);

            QueryScorer scorer = new QueryScorer(query, "title");
            Highlighter highlighter = new Highlighter(scorer);
            highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer));

            // Iterate through the results:
            for (int i = 0; i < hits.length; i++) {
                Document hitDoc = isearcher.doc(hits[i].doc);
                String title = hitDoc.get("title");
                assertEquals("This is the text to be indexed.", title);

                TokenStream stream = TokenSources.getAnyTokenStream(isearcher.getIndexReader(),
                        hits[i].doc,
                        "title",
                        hitDoc,
                        analyzer);
                String fragment = highlighter.getBestFragment(stream, title);
                assertEquals("This is the <B>text</B> to be indexed.", fragment);
            }
            ireader.close();
            directory.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void withFastVectorHighlighter() {
        try {
            Analyzer analyzer = new StandardAnalyzer();

            final String[] DOCS = {
                    "the quick brown fox jumps over the lazy dog",
                    "the quick gold fox jumped over the lazy black dog",
                    "the quick fox jumps over the black dog",
                    "the red fox jumped over the lazy dark gray dog"
            };
            final String fieldName = "title";

            Directory directory = TestUtils.index(fieldName, DOCS, analyzer);

            // Now search the index:

            // User's search string
            final String userQuery = "quick OR fox OR \"lazy dog\"~1";
            // final String userQuery = "quick OR fox OR (lazy dog)";

            // Get a searcher
            DirectoryReader ireader = DirectoryReader.open(directory);
            IndexSearcher isearcher = new IndexSearcher(ireader);

            // Parse the end-user's search string:
            QueryParser parser = new QueryParser(fieldName, analyzer);
            Query query = parser.parse(userQuery);

            // Search
            TopDocs docs = isearcher.search(query, 10);

            // Highlight results
            FastVectorHighlighter highlighter = getFastVectorHighlighter();
            FieldQuery fieldQuery = highlighter.getFieldQuery(query);

            FileWriter writer = new FileWriter("./withFastVectorHighlighter.html");
            writer.write("<html>");
            writer.write("<body>");
            writer.write("<p>QUERY: " + userQuery + "</p>");

            for (ScoreDoc scoreDoc : docs.scoreDocs) {
                String snippet = highlighter.getBestFragment(
                        fieldQuery, isearcher.getIndexReader(),
                        scoreDoc.doc, fieldName, 100);
                System.out.println(String.format("Doc %s snippet: %s", scoreDoc.doc, snippet));
                writer.write(String.format("<p>Doc %s snippet: %s</p>", scoreDoc.doc, snippet));
            }

            writer.write("</body></html>");
            writer.close();

            ireader.close();
            directory.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void withUnifiedHighlighter() {
        try {
            Analyzer analyzer = new StandardAnalyzer();

            final String[] DOCS = {
                    "the quick brown fox jumps over the lazy dog",
                    "the quick gold fox jumped over the lazy black dog",
                    "the quick fox jumps over the black dog",
                    "the red fox jumped over the lazy dark gray dog"
            };
            final String fieldName = "title";

            Directory directory = TestUtils.index(fieldName, DOCS, analyzer);

            // Now search the index:

            // User's search string
            final String userQuery = "quick OR fox OR \"lazy dog\"~1";
            // final String userQuery = "quick OR fox OR (lazy dog)";

            // Get a searcher
            DirectoryReader ireader = DirectoryReader.open(directory);
            IndexSearcher isearcher = new IndexSearcher(ireader);

            // Parse the end-user's search string:
            QueryParser parser = new QueryParser(fieldName, analyzer);
            Query query = parser.parse(userQuery);

            // Search
            TopDocs docs = isearcher.search(query, 10);

            // Highlight results
            UnifiedHighlighter highlighter = getUnifiedHighlighter(isearcher, analyzer);

            FileWriter writer = new FileWriter("./withUnifiedHighlighter.html");
            writer.write("<html>");
            writer.write("<body>");
            writer.write("<p>QUERY: " + userQuery + "</p>");

            String[] fragments = highlighter.highlight(fieldName, query, docs);

            for (int i = 0; i < docs.scoreDocs.length; i++) {
                ScoreDoc scoreDoc = docs.scoreDocs[i];
                System.out.println(String.format("Doc %s snippet: %s", scoreDoc.doc, fragments[i]));
                writer.write(String.format("<p>Doc %s snippet: %s</p>", scoreDoc.doc, fragments[i]));
            }

            writer.write("</body></html>");
            writer.close();

            ireader.close();
            directory.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Instantiate a fast vector highlighter.
     *
     * @return The instantiated highlighter.
     */
    FastVectorHighlighter getFastVectorHighlighter() {
        FragListBuilder fragListBuilder = new SimpleFragListBuilder();
        FragmentsBuilder fragmentBuilder =
                new ScoreOrderFragmentsBuilder(
                        BaseFragmentsBuilder.COLORED_PRE_TAGS,
                        BaseFragmentsBuilder.COLORED_POST_TAGS);
        return new FastVectorHighlighter(true, true,
                fragListBuilder, fragmentBuilder);
    }

    /**
     * Instantiate a unified highlighter.
     *
     * @return The instantiated highlighter.
     */
    UnifiedHighlighter getUnifiedHighlighter(IndexSearcher indexSearcher, Analyzer indexAnalyzer) {
        UnifiedHighlighter highlighter = new UnifiedHighlighter(indexSearcher, indexAnalyzer);
        highlighter.setHighlightPhrasesStrictly(true);
        return highlighter;
    }
}
