package com.securechat.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.hibernate.type.descriptor.sql.spi.DdlTypeRegistry;

/**
 * Custom H2 dialect that maps PostgreSQL NAMED_ENUM types to VARCHAR.
 *
 * <p>The production entities use {@code @JdbcTypeCode(SqlTypes.NAMED_ENUM)}
 * for enum columns, which works with PostgreSQL's native enum support.
 * H2 does not support this type code, so this dialect maps it to VARCHAR
 * to allow integration tests to run with H2 in-memory database.
 *
 * <p>Only loaded in the "test" profile via application-test.yml.
 */
public class H2NamedEnumDialect extends H2Dialect {

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);

        // Register a JdbcType for NAMED_ENUM that behaves like VARCHAR
        // so Hibernate can read/write enum values as strings in H2.
        typeContributions.contributeJdbcType(new VarcharJdbcType() {
            @Override
            public int getJdbcTypeCode() {
                return SqlTypes.NAMED_ENUM;
            }

            @Override
            public String toString() {
                return "VarcharJdbcType(NAMED_ENUM)";
            }
        });

        // Also register the DDL type so Hibernate can generate CREATE TABLE DDL
        // that maps NAMED_ENUM columns to varchar(255) in H2.
        DdlTypeRegistry ddlTypeRegistry = typeContributions.getTypeConfiguration()
                .getDdlTypeRegistry();
        ddlTypeRegistry.addDescriptor(
                new DdlTypeImpl(SqlTypes.NAMED_ENUM, "varchar(255)", this)
        );
    }
}
