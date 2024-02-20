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

package com.github.springtestdbunit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;

import com.github.springtestdbunit.annotation.*;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.AbstractDatabaseConnection;
import org.dbunit.database.FullyLoadedTable;
import org.dbunit.database.ResultSetTable;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.filter.IColumnFilter;
import org.dbunit.junit.internal.TestContext;
import org.junit.runners.model.MultipleFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.github.springtestdbunit.assertion.DatabaseAssertion;
import com.github.springtestdbunit.dataset.DataSetLoader;
import com.github.springtestdbunit.dataset.DataSetModifier;
import com.github.springtestdbunit.operation.DatabaseOperationLookup;

/**
 * Internal use. Can be changed or removed at any time
 *
 * @author Vasiliy Gagin
 */
public class DbUnitRunner {

    private static final Logger logger = LoggerFactory.getLogger(DbUnitRunner.class);

    public void beforeTestMethod(Class<?> testClass, Method testMethod, TestContext dbunitTestContext,
            DataSetLoader dataSetLoader, DatabaseOperationLookup databaseOperationLookup)
            throws Exception {
        setupOrTeardown(testClass, testMethod, DatabaseSetup.class, dbunitTestContext, dataSetLoader,
                databaseOperationLookup);
    }

    public Throwable afterTestMethod(Class<?> testClass, Object testInstance, Method testMethod,
            Throwable testException, TestContext dbunitTestContext, DataSetLoader dataSetLoader,
            DatabaseOperationLookup databaseOperationLookup)
            throws Exception, DataSetException, SQLException, DatabaseUnitException {
        if (testException != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping @DatabaseTest expectation due to test exception " + testException);
            }
        } else {
            try {
                verifyExpected(testClass, testInstance, testMethod, dbunitTestContext, dataSetLoader);
            } catch (Throwable re) {
                testException = re;
            }
        }

        try {
            setupOrTeardown(testClass, testMethod, DatabaseTearDown.class, dbunitTestContext, dataSetLoader,
                    databaseOperationLookup);
        } catch (RuntimeException ex) {
            if (testException == null) {
                testException = ex;
            } else {
                testException = new MultipleFailureException(Arrays.asList(testException, ex));
            }
            if (logger.isWarnEnabled()) {
                logger.warn("Unable to throw database cleanup exception due to existing test error", ex);
            }
        }
        return testException;
    }

    private void verifyExpected(Class<?> testClass, Object testInstance, Method testMethod,
            TestContext dbunitTestContext, DataSetLoader dataSetLoader)
            throws Exception, DataSetException, SQLException, DatabaseUnitException {
        Annotations<ExpectedDatabase> annotations = new Annotations<>(testClass, testMethod, ExpectedDatabase.class);
        DataSetModifier modifier = getModifier(testInstance, annotations); // Not sure why modifiers are combined
        boolean override = false;
        for (ExpectedDatabase annotation : annotations.getMethodAnnotations()) {
            verifyExpected(dataSetLoader, testClass, dbunitTestContext, modifier, annotation, testMethod);
            override |= annotation.override();
        }
        if (!override) {
            for (ExpectedDatabase annotation : annotations.getClassAnnotations()) {
                verifyExpected(dataSetLoader, testClass, dbunitTestContext, modifier, annotation, testMethod);
            }
        }
    }

    private void verifyExpected(DataSetLoader dataSetLoader, Class<?> testClass, TestContext dbunitTestContext,
            DataSetModifier modifier, ExpectedDatabase annotation, Method testMethod)
            throws Exception, SQLException, DatabaseUnitException {
        String query = annotation.query();
        String table = annotation.table();
        IDataSet expectedDataSet = loadResourceDataset(dataSetLoader, testClass, annotation.value(), modifier);
        AbstractDatabaseConnection connection = dbunitTestContext.getConnection(annotation.connection());
        if (expectedDataSet != null) {
            DatabaseAssertion assertion = annotation.assertionMode().getDatabaseAssertion();
            List<IColumnFilter> columnFilters = getColumnFilters(testClass, testMethod, annotation);
            List<String> ignoredColumns = getIgnoredColumns(annotation);
            if (StringUtils.hasLength(table)) {
                ITable expectedTable = expectedDataSet.getTable(table);
                ResultSetTable resultSetTable;
                if (StringUtils.hasLength(query)) {
                    resultSetTable = connection.loadTableResultSetViaQuery(table, query);
                } else {
                    resultSetTable = connection.loadTableResultSet(table);
                }
                FullyLoadedTable actualTable = new FullyLoadedTable(resultSetTable);
                assertion.assertEquals(expectedTable, actualTable, columnFilters, ignoredColumns);
            } else {
                // whole database compare !? Wonder the use of this
                IDataSet actualDataSet = connection.createDataSet();
                assertion.assertEquals(expectedDataSet, actualDataSet, columnFilters, ignoredColumns);
            }
        }
    }

    private DataSetModifier getModifier(Object testInstance, Annotations<ExpectedDatabase> annotations) {
        DataSetModifiers modifiers = new DataSetModifiers();
        for (ExpectedDatabase annotation : annotations) {
            for (Class<? extends DataSetModifier> modifierClass : annotation.modifiers()) {
                modifiers.add(testInstance, modifierClass);
            }
        }
        return modifiers;
    }

    private <T extends Annotation> void setupOrTeardown(Class<?> testClass, Method testMethod, Class<T> annotationClass,
            TestContext dbunitTestContext, DataSetLoader dataSetLoader, DatabaseOperationLookup databaseOperationLookup)
            throws Exception, SQLException, DatabaseUnitException {
        Annotations<T> annotations = new Annotations<>(testClass, testMethod, annotationClass);
        for (T annotation : annotations) {
            Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
            String[] dataSetLocations = (String[]) attributes.get("value");
            String connectionName = (String) attributes.get("connection");
            DatabaseOperation operation = (DatabaseOperation) attributes.get("type");
            logger.debug("Executing annotation {} using {} and locations {}", annotationClass, operation,
                    dataSetLocations);

            executeOperation(testClass, dbunitTestContext, dataSetLoader, databaseOperationLookup, dataSetLocations,
                    connectionName, operation);
        }
    }

    private void executeOperation(Class<?> testClass, TestContext dbunitTestContext, DataSetLoader dataSetLoader,
            DatabaseOperationLookup databaseOperationLookup, String[] dataSetLocations, String connectionName,
            DatabaseOperation operation) throws Exception, SQLException, DatabaseUnitException {

        AbstractDatabaseConnection databaseConnection = dbunitTestContext.getConnection(connectionName);
        org.dbunit.operation.DatabaseOperation dbUnitOperation = getDbUnitDatabaseOperation(databaseOperationLookup,
                operation);
        IDataSet dataSet = loadDataSet(testClass, dataSetLoader, dataSetLocations, databaseConnection);
        dbUnitOperation.execute(databaseConnection, dataSet);
    }

    private IDataSet loadDataSet(Class<?> testClass, DataSetLoader dataSetLoader, String[] dataSetLocations,
            AbstractDatabaseConnection databaseConnection) throws Exception, SQLException, DataSetException {
        if (dataSetLocations.length == 0) {
            return databaseConnection.createDataSet();
        }
        IDataSet[] datasets = new IDataSet[dataSetLocations.length];
        for (int i = 0; i < dataSetLocations.length; ++i) {
            datasets[i] = loadResourceDataset(dataSetLoader, testClass, dataSetLocations[i], DataSetModifier.NONE);
        }
        return new CompositeDataSet(datasets);
    }

    private IDataSet loadResourceDataset(DataSetLoader dataSetLoader, Class<?> testClass, String dataSetLocation,
            DataSetModifier modifier) throws Exception {
        if (StringUtils.hasLength(dataSetLocation)) {
            IDataSet dataSet = dataSetLoader.loadDataSet(testClass, dataSetLocation);
            dataSet = modifier.modify(dataSet);
            Assert.notNull(dataSet,
                    "Unable to load dataset from \"" + dataSetLocation + "\" using " + dataSetLoader.getClass());
            return dataSet;
        }
        return null;
    }

    private List<IColumnFilter> getColumnFilters(ExpectedDatabase annotation) throws Exception {
        Class<? extends IColumnFilter>[] columnFilterClasses = annotation.columnFilters();
        List<IColumnFilter> columnFilters = new LinkedList<>();
        for (Class<? extends IColumnFilter> columnFilterClass : columnFilterClasses) {
            columnFilters.add(columnFilterClass.getDeclaredConstructor().newInstance());
        }
        return columnFilters;
    }

    private List<IColumnFilter> getColumnFilters(Class<?> testClass, Method testMethod, ExpectedDatabase annotation) throws Exception {
        List<Class<? extends IColumnFilter>> fromDbUnitConfiguration = getColumnFiltersFromDbUnitConfiguration(testClass, testMethod);
        Class<? extends IColumnFilter>[] fromExpectedDatabase = getColumnFiltersFromExpectedDatabase(annotation);
        Class<? extends IColumnFilter>[] columnFilterClasses = mergeDistinct(fromDbUnitConfiguration, fromExpectedDatabase);
        List<IColumnFilter> columnFilters = new LinkedList<>();
        for (Class<? extends IColumnFilter> columnFilterClass : columnFilterClasses) {
            columnFilters.add(columnFilterClass.getDeclaredConstructor().newInstance());
        }
        return columnFilters;
    }

    private Class<? extends IColumnFilter>[] getColumnFiltersFromExpectedDatabase(ExpectedDatabase annotation) {
        Class<? extends IColumnFilter>[] columnFilterClasses = annotation.columnFilters();
        if (logger.isDebugEnabled()) {
            logger.debug("Found columnFilters on @ExpectedDatabase configuration");
        }
        return columnFilterClasses;
    }

    private List<Class<? extends IColumnFilter>> getColumnFiltersFromDbUnitConfiguration(Class<?> testClass, Method testMethod) {
        List<Class<? extends IColumnFilter>> columnFilterClasses = new LinkedList<>();
        Annotations<DbUnitConfiguration> annotations = new Annotations<>(testClass, testMethod, DbUnitConfiguration.class);
        if (annotations != null) {
            for (DbUnitConfiguration annotation : annotations) {
                for (Class<? extends IColumnFilter> clazz : annotation.columnFilters()) {
                    columnFilterClasses.add(clazz);
                }
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Found columnFilters on @DbUnitConfiguration configuration");
            }
        }
        return columnFilterClasses;
    }

    private List<String> getIgnoredColumns(ExpectedDatabase annotation) {
        return Arrays.asList(annotation.ignoreCols());
    }

    private Class<? extends IColumnFilter>[] mergeDistinct(List<Class<? extends IColumnFilter>> first, Class<? extends IColumnFilter>[] second) {
        Set<Class<? extends IColumnFilter>> result = new HashSet<>();
        result.addAll(first);
        result.addAll(Arrays.asList(second));
        return result.toArray(new Class[0]);
    }

    private org.dbunit.operation.DatabaseOperation getDbUnitDatabaseOperation(
            DatabaseOperationLookup databaseOperationLookup, DatabaseOperation operation) {
        org.dbunit.operation.DatabaseOperation databaseOperation = databaseOperationLookup.get(operation);
        Assert.state(databaseOperation != null, "The database operation " + operation + " is not supported");
        return databaseOperation;
    }

}
