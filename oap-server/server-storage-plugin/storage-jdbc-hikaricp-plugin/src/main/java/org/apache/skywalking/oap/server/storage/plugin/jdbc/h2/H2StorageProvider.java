/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2;

import java.util.Properties;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.ClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2BatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2EndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetricQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2NetworkAddressInventoryCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2RegisterLockDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2StorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopologyQueryDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wusheng
 */
public class H2StorageProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(H2StorageProvider.class);

    private H2StorageConfig config;
    private JDBCHikariCPClient h2Client;

    public H2StorageProvider() {
        config = new H2StorageConfig();
    }

    @Override public String name() {
        return "h2";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        Properties settings = new Properties();
        settings.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource");
        settings.setProperty("dataSource.url", "jdbc:h2:mem:collector");
        settings.setProperty("dataSource.user", "");
        settings.setProperty("dataSource.password", "");
        h2Client = new JDBCHikariCPClient(settings);

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(h2Client));
        this.registerServiceImplementation(StorageDAO.class, new H2StorageDAO(h2Client));
        this.registerServiceImplementation(IRegisterLockDAO.class, new H2RegisterLockDAO());

        this.registerServiceImplementation(IServiceInventoryCacheDAO.class, new H2ServiceInventoryCacheDAO(h2Client));
        this.registerServiceImplementation(IServiceInstanceInventoryCacheDAO.class, new H2ServiceInstanceInventoryCacheDAO(h2Client));
        this.registerServiceImplementation(IEndpointInventoryCacheDAO.class, new H2EndpointInventoryCacheDAO(h2Client));
        this.registerServiceImplementation(INetworkAddressInventoryCacheDAO.class, new H2NetworkAddressInventoryCacheDAO(h2Client));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new H2TopologyQueryDAO(h2Client));
        this.registerServiceImplementation(IMetricQueryDAO.class, new H2MetricQueryDAO(h2Client));
        //this.registerServiceImplementation(ITraceQueryDAO.class, new TraceQueryEsDAO(elasticSearchClient));
        //this.registerServiceImplementation(IMetadataQueryDAO.class, new MetadataQueryEsDAO(elasticSearchClient));
        //this.registerServiceImplementation(IAggregationQueryDAO.class, new AggregationQueryEsDAO(elasticSearchClient));

    }

    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
        try {
            h2Client.initialize();

            H2TableInstaller installer = new H2TableInstaller(getManager());
            installer.install(h2Client);

            new H2RegisterLockInstaller().install(h2Client);
        } catch (StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        } catch (ClientException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }
}