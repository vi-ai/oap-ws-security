/*
 * The MIT License (MIT)
 *
 * Copyright (c) Open Application Platform Authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package oap.ws.security.server;

import oap.application.Application;
import oap.concurrent.SynchronizedThread;
import oap.http.PlainHttpListener;
import oap.http.Server;
import oap.io.Resources;
import oap.json.TypeIdFactory;
import oap.testng.Env;
import oap.ws.SessionManager;
import oap.ws.WebServices;
import oap.ws.WsConfig;
import oap.ws.security.domain.Organization;
import oap.ws.security.domain.Role;
import oap.ws.security.domain.User;
import org.apache.http.entity.ContentType;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static oap.http.testng.HttpAsserts.*;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

public class OrganizationWSTest {

    private final Server server = new Server( 100 );
    private final WebServices webServices = new WebServices( server, new SessionManager( 10, null, "/" ),
        WsConfig.CONFIGURATION.fromResource( getClass(), "ws-organization.conf" ) );

    private UserStorage userStorage;
    private OrganizationStorage organizationStorage;

    private SynchronizedThread listener;

    @BeforeClass
    public void startServer() {
        userStorage = new UserStorage( Resources.filePath( OrganizationWSTest.class, "" ).get() );
        organizationStorage = new OrganizationStorage( Resources.filePath( OrganizationWSTest.class, "" ).get() );

        userStorage.start();
        organizationStorage.start();

        Application.register( "ws-organization", new OrganizationWS( organizationStorage, userStorage, "test" ) );

        webServices.start();
        listener = new SynchronizedThread( new PlainHttpListener( server, Env.port() ) );
        listener.start();
    }

    @AfterClass
    public void stopServer() {
        listener.stop();
        server.stop();
        webServices.stop();
        reset();
    }

    @BeforeMethod
    public void setUp() {
        userStorage.clear();
        organizationStorage.clear();
    }

    @Test
    public void testShouldStoreGetDeleteOrganization() {
        final String request = Resources.readString( getClass(), getClass().getSimpleName() + "/"
            + "12345.json" ).get();

        assertPost( HTTP_PREFIX + "/organization/store", request, ContentType.APPLICATION_JSON )
            .hasCode( 204 );
        assertGet( HTTP_PREFIX + "/organization/12345" )
            .hasCode( 200 ).hasBody( "{\"id\":\"12345\",\"name\":\"test\",\"description\":\"test organization\"}" );
        assertDelete( HTTP_PREFIX + "/organization/remove/12345" ).hasCode( 204 );

        assertFalse( organizationStorage.get( "12345" ).isPresent() );
    }

    @Test( expectedExceptions = { IllegalStateException.class },
        expectedExceptionsMessageRegExp = "Organization 98765 doesn't exists" )
    public void testShouldNotStoreUserIfOrganizationDoesNotExist() {
        final OrganizationWS organizationWS = new OrganizationWS( organizationStorage, userStorage, "test" );
        final User user = new User();
        user.email = "test@example.com";
        user.password = "123456789";
        user.role = Role.USER;
        user.organizationId = "12345";
        user.organizationName = "test";

        organizationWS.storeUser( user, "98765", new User() );
    }

    @Test( expectedExceptions = { IllegalStateException.class },
        expectedExceptionsMessageRegExp = "Cannot save user test@example.com with organization 12345" +
            " to organization 98765" )
    public void testShouldNotStoreUserIfOrganizationMismatch() {
        final OrganizationWS organizationWS = new OrganizationWS( organizationStorage, userStorage, "test" );
        final User user = new User();
        user.email = "test@example.com";
        user.password = "123456789";
        user.role = Role.USER;
        user.organizationId = "12345";
        user.organizationName = "test";

        final Organization organization = new Organization();
        organization.id = "98765";
        organizationStorage.store( organization );

        organizationWS.storeUser( user, "98765", new User() );
    }

    @Test( expectedExceptions = { IllegalStateException.class },
        expectedExceptionsMessageRegExp = "User test@example.com is already present in another organization" )
    public void testShouldNotStoreUserIfItExistsInAnotherOrganization() {
        final OrganizationWS organizationWS = new OrganizationWS( organizationStorage, userStorage, "test" );
        final User user = new User();
        user.email = "test@example.com";
        user.password = "123456789";
        user.role = Role.USER;
        user.organizationId = "12345";
        user.organizationName = "test";

        userStorage.store( user );

        final Organization organizationA = new Organization();
        organizationA.id = "12345";
        organizationStorage.store( organizationA );

        final Organization organizationB = new Organization();
        organizationB.id = "98765";
        organizationStorage.store( organizationB );

        final User userUpdate = new User();
        userUpdate.email = "test@example.com";
        userUpdate.organizationId = "98765";
        organizationWS.storeUser( userUpdate, "98765", new User() );
    }

    @Test
    public void testShouldSaveUserIfSessionUserIsAdmin() {
        final OrganizationWS organizationWS = new OrganizationWS( organizationStorage, userStorage, "test" );
        final User user = new User();
        user.email = "test@example.com";
        user.password = "123456789";
        user.role = Role.USER;
        user.organizationId = "12345";
        user.organizationName = "test";

        final Organization organization = new Organization();
        organization.id = "12345";
        organizationStorage.store( organization );

        final User sessionUser = new User();
        sessionUser.role = Role.ADMIN;

        organizationWS.storeUser( user, "12345", sessionUser );

        assertNotNull( userStorage.get( "test@example.com" ).isPresent() );
    }

    @Test( expectedExceptions = { IllegalStateException.class },
        expectedExceptionsMessageRegExp = "User with role ORGANIZATION_ADMIN can't grant role ADMIN to user " +
            "test@example.com")
    public void testShouldNotSaveUserWithHigherRoleThanSessionUserIfNotAdmin() {
        final OrganizationWS organizationWS = new OrganizationWS( organizationStorage, userStorage, "test" );
        final User user = new User();
        user.email = "test@example.com";
        user.password = "123456789";
        user.role = Role.ADMIN;
        user.organizationId = "12345";
        user.organizationName = "test";

        final Organization organization = new Organization();
        organization.id = "12345";
        organizationStorage.store( organization );

        final User sessionUser = new User();
        sessionUser.role = Role.ORGANIZATION_ADMIN;

        organizationWS.storeUser( user, "12345", sessionUser );
    }

    @Test( expectedExceptions = { IllegalStateException.class },
        expectedExceptionsMessageRegExp = "User org-admin@example.com cannot operate on users from " +
            "different organization 12345")
    public void testShouldNotSaveUserIfSessionUserHasDifferentOrganization() {
        final OrganizationWS organizationWS = new OrganizationWS( organizationStorage, userStorage, "test" );
        final User user = new User();
        user.email = "test@example.com";
        user.password = "123456789";
        user.role = Role.USER;
        user.organizationId = "12345";
        user.organizationName = "test";

        final Organization organization = new Organization();
        organization.id = "12345";
        organizationStorage.store( organization );

        final User sessionUser = new User();
        sessionUser.email = "org-admin@example.com";
        sessionUser.organizationId = "98765";
        sessionUser.role = Role.ORGANIZATION_ADMIN;

        organizationWS.storeUser( user, "12345", sessionUser );
    }

    @Test
    public void testShouldSaveUserIfSessionUserIsOrganizationAdmin() {
        final OrganizationWS organizationWS = new OrganizationWS( organizationStorage, userStorage, "test" );
        final User user = new User();
        user.email = "test@example.com";
        user.password = "123456789";
        user.role = Role.USER;
        user.organizationId = "12345";
        user.organizationName = "test";

        final Organization organization = new Organization();
        organization.id = "12345";
        organizationStorage.store( organization );

        final User sessionUser = new User();
        sessionUser.organizationId = "12345";
        sessionUser.role = Role.ORGANIZATION_ADMIN;

        organizationWS.storeUser( user, "12345", sessionUser );

        assertNotNull( userStorage.get( "test@example.com" ).isPresent() );
    }
}