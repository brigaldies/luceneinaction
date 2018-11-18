package com.o19s.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QueryParserTest {

    @Test
    public void testQueryParserWildcard() {

        try {

            Analyzer analyzer = new StandardAnalyzer();

            final String[] DOCS = {
                    "the quick brown fox jumps over the lazy dog"
            };

            Directory directory = TestUtils.index("title", DOCS, analyzer);

            // Now search the index:
            DirectoryReader ireader = DirectoryReader.open(directory);
            IndexSearcher isearcher = new IndexSearcher(ireader);

            // Parse a simple query that searches for "text":
            QueryParser parser = new QueryParser("title", analyzer);

            String[] test_queries = new String[]{
                    "qui*",
                    "qu*ck",
                    "quic?",
                    "qu?ck"
            };
            for (String query_string : test_queries) {
                Query query = parser.parse(query_string);

                System.out.println(String.format("End-user's query: %s --> Parsed query: %s", query_string, query.toString()));

                ScoreDoc[] hits = isearcher.search(query, 10).scoreDocs;
                assertEquals(1, hits.length);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
