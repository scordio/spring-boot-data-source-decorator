/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gavlyukovskiy.boot.jdbc.decorator;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.P6DataSource;
import com.vladmihalcea.flexypool.config.PropertyLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class P6SpyAutoconfigurationTests {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @Before
    public void init() {
        EnvironmentTestUtils.addEnvironment(context,
                "spring.datasource.initialize:false",
                "spring.datasource.url:jdbc:h2:mem:testdb-" + new Random().nextInt());
        context.setClassLoader(new HidePackagesClassLoader("com.vladmihalcea.flexypool", "net.ttddyy.dsproxy"));
    }

    @After
    public void restore() {
        System.clearProperty(PropertyLoader.PROPERTIES_FILE_PATH);
        context.close();
    }

    @Test
    public void testCustomListeners() throws Exception {
        context.register(CustomListenerConfiguration.class,
                DataSourceAutoConfiguration.class,
                DataSourceDecoratorAutoConfiguration.class,
                PropertyPlaceholderAutoConfiguration.class);
        context.refresh();

        DataSource dataSource = context.getBean(DataSource.class);
        WrappingCountingListener wrappingCountingListener = context.getBean(WrappingCountingListener.class);
        ClosingCountingListener closingCountingListener = context.getBean(ClosingCountingListener.class);
        P6DataSource p6DataSource = (P6DataSource) ((DecoratedDataSource) dataSource).getDecoratedDataSource();

        assertThat(wrappingCountingListener.wrappedCount).isEqualTo(0);

        Connection connection = p6DataSource.getConnection();

        assertThat(wrappingCountingListener.wrappedCount).isEqualTo(1);
        assertThat(closingCountingListener.wrappedCount).isEqualTo(0);

        connection.close();

        assertThat(closingCountingListener.wrappedCount).isEqualTo(1);
    }

    @Configuration
    static class CustomListenerConfiguration {

        @Bean
        public WrappingCountingListener wrappingCountingListener() {
            return new WrappingCountingListener();
        }

        @Bean
        public ClosingCountingListener closingCountingListener() {
            return new ClosingCountingListener();
        }
    }

    static class WrappingCountingListener extends JdbcEventListener {

        int wrappedCount = 0;

        @Override
        public void onConnectionWrapped(ConnectionInformation connectionInformation) {
            wrappedCount++;
        }
    }

    static class ClosingCountingListener extends JdbcEventListener {

        int wrappedCount = 0;

        @Override
        public void onAfterConnectionClose(ConnectionInformation connectionInformation, SQLException e) {
            wrappedCount++;
        }
    }
}