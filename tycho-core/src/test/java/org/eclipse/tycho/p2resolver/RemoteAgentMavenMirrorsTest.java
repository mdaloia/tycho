/*******************************************************************************
 * Copyright (c) 2012 SAP AG and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2resolver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.net.URI;
import java.util.Properties;

import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.tycho.IRepositoryIdManager;
import org.eclipse.tycho.MavenRepositorySettings;
import org.eclipse.tycho.agent.RemoteAgent;
import org.eclipse.tycho.core.shared.MavenContext;
import org.eclipse.tycho.core.test.utils.ResourceUtil;
import org.eclipse.tycho.p2maven.repository.DefaultMavenRepositorySettings;
import org.eclipse.tycho.test.util.HttpServer;
import org.eclipse.tycho.test.util.LogVerifier;
import org.eclipse.tycho.test.util.MockMavenContext;
import org.eclipse.tycho.testing.TychoPlexusTestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RemoteAgentMavenMirrorsTest extends TychoPlexusTestCase {

    private static final boolean OFFLINE = false;

    @Rule
    public final TemporaryFolder tempManager = new TemporaryFolder();
    @Rule
    public final LogVerifier logVerifier = new LogVerifier();
    @Rule
    public HttpServer localServer = new HttpServer();

    private DefaultMavenRepositorySettings mavenRepositorySettings;
    private IProvisioningAgent subject;

    @Before
    public void initSubject() throws Exception {
        File localRepository = tempManager.newFolder("localRepo");
        MavenContext mavenContext = new MockMavenContext(localRepository, OFFLINE, logVerifier.getMavenLogger(),
                new Properties()) {
            @Override
            public boolean isUpdateSnapshots() {
                return true;
            }
        };

        mavenRepositorySettings = (DefaultMavenRepositorySettings) lookup(MavenRepositorySettings.class);
        IProxyService service = null;
        subject = new RemoteAgent(mavenContext, service, mavenRepositorySettings, OFFLINE,
                lookup(IProvisioningAgent.class));
    }

    @Test
    public void testLoadFromOriginalLocation() throws Exception {
        String repositoryId = "other-id";
        URI url = URI.create(localServer.addServlet("original", ResourceUtil.resourceFile("repositories/e342")));

        Repositories repos = loadRepositories(repositoryId, url);

        assertNotNull(repos.getMetadataRepository());
        assertNotNull(repos.getArtifactRepository());
    }

    @Test
    public void testLoadFromMirroredLocation() throws Exception {
        String repositoryId = "well-known-id";
        URI originalUrl = URI.create(localServer.addServlet("original", noContent())); // will fail if used
        URI mirroredUrl = URI
                .create(localServer.addServlet("mirrored", ResourceUtil.resourceFile("repositories/e342")));
        try {
            prepareMavenMirrorConfiguration(repositoryId, mirroredUrl);

            Repositories repos = loadRepositories(repositoryId, originalUrl);

            assertNotNull(repos.getMetadataRepository());
            assertNotNull(repos.getArtifactRepository());
        } finally {
            prepareMavenMirrorConfiguration(repositoryId, null);
        }
    }

    @Test
    public void testLoadFromMirroredLocationWithFallbackId() throws Exception {
        URI originalUrl = URI.create(localServer.addServlet("original", noContent())); // will fail if used
        URI mirroredUrl = URI
                .create(localServer.addServlet("mirrored", ResourceUtil.resourceFile("repositories/e342")));
        String repositoryFallbackId = originalUrl.toString();
        assertFalse("self-test: fallback ID shall be URL without trailing slash", repositoryFallbackId.endsWith("/"));
        try {
            prepareMavenMirrorConfiguration(repositoryFallbackId, mirroredUrl);

            Repositories repos = loadRepositories(null, originalUrl);

            assertNotNull(repos.getMetadataRepository());
            assertNotNull(repos.getArtifactRepository());
        } finally {
            prepareMavenMirrorConfiguration(repositoryFallbackId, null);
        }
    }

    private void prepareMavenMirrorConfiguration(String id, URI mirrorUrl) {
        mavenRepositorySettings.addMirror(id, mirrorUrl);
    }

    private File noContent() throws Exception {
        return tempManager.newFolder("empty");
    }

    private Repositories loadRepositories(String id, URI specifiedUrl) throws Exception {

        IRepositoryIdManager idManager = subject.getService(IRepositoryIdManager.class);
        idManager.addMapping(id, specifiedUrl);

        IMetadataRepositoryManager metadataManager = subject.getService(IMetadataRepositoryManager.class);
        IMetadataRepository metadataRepo = metadataManager.loadRepository(specifiedUrl, null);

        IArtifactRepositoryManager artifactsManager = subject.getService(IArtifactRepositoryManager.class);
        IArtifactRepository artifactsRepo = artifactsManager.loadRepository(specifiedUrl, null);

        return new Repositories(metadataRepo, artifactsRepo);
    }

    static class Repositories {
        private final IMetadataRepository metadataRepository;
        private final IArtifactRepository artifactRepository;

        Repositories(IMetadataRepository metadataRepository, IArtifactRepository artifactRepository) {
            this.metadataRepository = metadataRepository;
            this.artifactRepository = artifactRepository;
        }

        public IMetadataRepository getMetadataRepository() {
            return metadataRepository;
        }

        public IArtifactRepository getArtifactRepository() {
            return artifactRepository;
        }
    }
}