/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.aliases;

import org.elasticsearch.common.compress.CompressedString;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.cache.IndexCacheModule;
import org.elasticsearch.index.engine.IndexEngineModule;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.IndexQueryParserModule;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.index.similarity.SimilarityModule;
import org.elasticsearch.indices.InvalidAliasNameException;
import org.elasticsearch.script.ScriptModule;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.elasticsearch.index.query.FilterBuilders.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * @author imotov
 */
public class IndexAliasesServiceTests {
    public static IndexAliasesService newIndexAliasesService() {
        return new IndexAliasesService(new Index("test"), ImmutableSettings.Builder.EMPTY_SETTINGS, newIndexQueryParserService());
    }

    public static IndexQueryParserService newIndexQueryParserService() {
        Injector injector = new ModulesBuilder().add(
                new IndexSettingsModule(new Index("test"), ImmutableSettings.Builder.EMPTY_SETTINGS),
                new IndexNameModule(new Index("test")),
                new IndexQueryParserModule(ImmutableSettings.Builder.EMPTY_SETTINGS),
                new AnalysisModule(ImmutableSettings.Builder.EMPTY_SETTINGS),
                new SimilarityModule(ImmutableSettings.Builder.EMPTY_SETTINGS),
                new ScriptModule(ImmutableSettings.Builder.EMPTY_SETTINGS),
                new SettingsModule(ImmutableSettings.Builder.EMPTY_SETTINGS),
                new IndexEngineModule(ImmutableSettings.Builder.EMPTY_SETTINGS),
                new IndexCacheModule(ImmutableSettings.Builder.EMPTY_SETTINGS)
        ).createInjector();
        return injector.getInstance(IndexQueryParserService.class);
    }

    public static CompressedString filter(FilterBuilder filterBuilder) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        filterBuilder.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.close();
        return new CompressedString(builder.string());
    }

    @Test public void testFilteringAliases() throws Exception {
        IndexAliasesService indexAliasesService = newIndexAliasesService();
        indexAliasesService.add("cats", filter(termFilter("animal", "cat")));
        indexAliasesService.add("dogs", filter(termFilter("animal", "dog")));
        indexAliasesService.add("all", null);

        assertThat(indexAliasesService.hasAlias("cats"), equalTo(true));
        assertThat(indexAliasesService.hasAlias("dogs"), equalTo(true));
        assertThat(indexAliasesService.hasAlias("turtles"), equalTo(false));

        assertThat(indexAliasesService.aliasFilter("cats").toString(), equalTo("FilterCacheFilterWrapper(animal:cat)"));
        assertThat(indexAliasesService.aliasFilter("cats", "dogs").toString(), equalTo("BooleanFilter( FilterCacheFilterWrapper(animal:cat) FilterCacheFilterWrapper(animal:dog))"));

        // Non-filtering alias should turn off all filters because filters are ORed
        assertThat(indexAliasesService.aliasFilter("all"), nullValue());
        assertThat(indexAliasesService.aliasFilter("cats", "all"), nullValue());
        assertThat(indexAliasesService.aliasFilter("all", "cats"), nullValue());

        indexAliasesService.add("cats", filter(termFilter("animal", "feline")));
        indexAliasesService.add("dogs", filter(termFilter("animal", "canine")));
        assertThat(indexAliasesService.aliasFilter("dogs", "cats").toString(), equalTo("BooleanFilter( FilterCacheFilterWrapper(animal:canine) FilterCacheFilterWrapper(animal:feline))"));
    }

    @Test public void testAliasFilters() throws Exception {
        IndexAliasesService indexAliasesService = newIndexAliasesService();
        indexAliasesService.add("cats", filter(termFilter("animal", "cat")));
        indexAliasesService.add("dogs", filter(termFilter("animal", "dog")));

        assertThat(indexAliasesService.aliasFilter(), nullValue());
        assertThat(indexAliasesService.aliasFilter("dogs").toString(), equalTo("FilterCacheFilterWrapper(animal:dog)"));
        assertThat(indexAliasesService.aliasFilter("dogs", "cats").toString(), equalTo("BooleanFilter( FilterCacheFilterWrapper(animal:dog) FilterCacheFilterWrapper(animal:cat))"));

        indexAliasesService.add("cats", filter(termFilter("animal", "feline")));
        indexAliasesService.add("dogs", filter(termFilter("animal", "canine")));

        assertThat(indexAliasesService.aliasFilter("dogs", "cats").toString(), equalTo("BooleanFilter( FilterCacheFilterWrapper(animal:canine) FilterCacheFilterWrapper(animal:feline))"));
    }

    @Test(expectedExceptions = InvalidAliasNameException.class) public void testRemovedAliasFilter() throws Exception {
        IndexAliasesService indexAliasesService = newIndexAliasesService();
        indexAliasesService.add("cats", filter(termFilter("animal", "cat")));
        indexAliasesService.remove("cats");
        indexAliasesService.aliasFilter("cats");
    }


    @Test(expectedExceptions = InvalidAliasNameException.class) public void testUnknownAliasFilter() throws Exception {
        IndexAliasesService indexAliasesService = newIndexAliasesService();
        indexAliasesService.add("cats", filter(termFilter("animal", "cat")));
        indexAliasesService.add("dogs", filter(termFilter("animal", "dog")));

        indexAliasesService.aliasFilter("unknown");
    }


}
