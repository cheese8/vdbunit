/*
 * Copyright 2002-2016 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.springtestdbunit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.sql.DataSource;

import com.github.springtestdbunit.assertion.DatabaseAssertionMode;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.dataset.DataSetLoader;
import com.github.springtestdbunit.dataset.FlatXmlDataSetLoader;
import com.github.springtestdbunit.operation.DatabaseOperationLookup;
import com.github.springtestdbunit.operation.DefaultDatabaseOperationLookup;
import org.dbunit.dataset.filter.IColumnFilter;

/**
 * Annotation that can be used to configure {@link DbUnitTestExecutionListener}.
 *
 * @see DbUnitTestExecutionListener
 * @author Phillip Webb
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface DbUnitConfiguration {

    /**
     * Returns the names of the spring beans which will be used to access
     * {@link IDatabaseConnection IDatabaseConnections}. The referenced beans can
     * either be an instance of {@link IDatabaseConnection} or a {@link DataSource}.
     *
     * @return the bean names of the database connections to be used
     */
    String[] databaseConnection() default {};

    /**
     * Returns name of spring bean to be used as a default connection. Specified
     * connection will be used if user did not identify connection name for an
     * operation.
     *
     * @return the bean name of default database connection
     */
    String defaultConnectionName() default "";

    /**
     * Returns the class that will be used to load {@link IDataSet} resources. The
     * specified class must implement {@link DataSetLoader} and must have a default
     * constructor.
     *
     * @return the data set loader class
     */
    Class<? extends DataSetLoader> dataSetLoader() default FlatXmlDataSetLoader.class;

    /**
     * Returns the name of the bean that will be used to load {@link IDataSet}
     * resources. The specified bean must implement {@link DataSetLoader}.
     *
     * @return the data set loader bean name
     */
    String dataSetLoaderBean() default "";

    /**
     * Returns the class that will be used to lookup DBUnit database operations. The
     * specific class must implement {@link DatabaseOperationLookup} and must have a
     * default constructor.
     *
     * @return the database operation lookup
     */
    Class<? extends DatabaseOperationLookup> databaseOperationLookup() default DefaultDatabaseOperationLookup.class;

    /**
     * A set of {@link org.dbunit.dataset.filter.IColumnFilter} that will be applied to column comparison when using
     * non-strict {@link DatabaseAssertionMode}.
     * <p>
     * Specify this when you want to use DTD with your expected dataset XML file but want to exclude some columns from
     * comparison.
     * @return column filters to apply
     */
    Class<? extends IColumnFilter>[] columnFilters() default {};
}
