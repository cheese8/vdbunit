/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.ext.postgresql;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter to handle conversion between Postgresql native UUID type and Strings.
 *
 * @author Jarvis Cochrane (jarvis@cochrane.com.au)
 * @author Last changed by: $Author$
 * @since Apr 27, 2009
 */
public class UuidType extends AbstractDataType {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(UuidType.class);

    public UuidType() {
        super("uuid", Types.OTHER, String.class, false);
    }

    public Object getSqlValue(int column, ResultSet resultSet) throws SQLException, TypeCastException {
        return resultSet.getString(column);
    }

    public void setSqlValue(Object uuid, int column, PreparedStatement statement)
            throws SQLException, TypeCastException {
        statement.setObject(column, getUUID(uuid, statement.getConnection()));
    }

    public Object typeCast(Object arg0) throws TypeCastException {
        return (arg0 == null) ? null : arg0.toString();
    }

    private Object getUUID(Object value, Connection connection) throws TypeCastException {

        logger.debug("getUUID(value={}, connection={}) - start", value, connection);

        Object tempUUID = null;

        try {
            Class aPGObjectClass = super.loadClass("org.postgresql.util.PGobject", connection);
            Constructor ct = aPGObjectClass.getConstructor(new Class[0]);
            tempUUID = ct.newInstance(new Object[0]);

            Method setTypeMethod = aPGObjectClass.getMethod("setType", new Class[] { String.class });
            setTypeMethod.invoke(tempUUID, new Object[] { "uuid" });

            Method setValueMethod = aPGObjectClass.getMethod("setValue", new Class[] { String.class });
            setValueMethod.invoke(tempUUID, new Object[] { value.toString() });

        } catch (ClassNotFoundException e) {
            throw new TypeCastException(value, this, e);
        } catch (InvocationTargetException e) {
            throw new TypeCastException(value, this, e);
        } catch (NoSuchMethodException e) {
            throw new TypeCastException(value, this, e);
        } catch (IllegalAccessException e) {
            throw new TypeCastException(value, this, e);
        } catch (InstantiationException e) {
            throw new TypeCastException(value, this, e);
        }

        return tempUUID;
    }
}
