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

import lombok.extern.slf4j.Slf4j;
import oap.util.Hash;
import oap.ws.WsMethod;
import oap.ws.WsParam;
import oap.ws.security.Organization;
import oap.ws.security.OrganizationAwareWS;
import oap.ws.security.Role;
import oap.ws.security.User;
import oap.ws.security.WsSecurity;
import oap.ws.validate.ValidationErrors;
import oap.ws.validate.WsValidate;

import java.util.List;
import java.util.Optional;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static oap.http.Request.HttpMethod.DELETE;
import static oap.http.Request.HttpMethod.GET;
import static oap.http.Request.HttpMethod.POST;
import static oap.ws.WsParam.From.BODY;
import static oap.ws.WsParam.From.PATH;
import static oap.ws.WsParam.From.SESSION;
import static oap.ws.security.Role.ADMIN;

@Slf4j
public class OrganizationWS implements OrganizationWSI, OrganizationAwareWS {

    private final OrganizationStorage organizationStorage;
    private final UserStorage userStorage;
    private final String salt;

    public OrganizationWS( OrganizationStorage organizationStorage, UserStorage userStorage, String salt ) {
        this.organizationStorage = organizationStorage;
        this.userStorage = userStorage;
        this.salt = salt;
    }

    @WsMethod( method = POST, path = "/store" )
    @WsSecurity( role = ADMIN )
    @Override
    public Organization store( @WsParam( from = BODY ) Organization organization ) {
        log.debug( "Storing organization: [{}]", organization );

        organizationStorage.store( organization );

        return organization;
    }

    @WsMethod( method = GET, path = "/" )
    @WsSecurity( role = ADMIN )
    @Override
    public List<Organization> list() {
        log.debug( "Fetching all organizations" );

        return organizationStorage.select().toList();
    }

    @WsMethod( method = GET, path = "/{organizationId}" )
    @WsSecurity( role = Role.USER )
    @WsValidate( { "validateOrganizationAccess" } )
    @Override
    public Optional<Organization> organization( @WsParam( from = PATH ) String organizationId,
                                                @WsParam( from = SESSION ) User user ) {
        return organizationStorage.get( organizationId );
    }

    @WsMethod( method = DELETE, path = "/{organizationId}" )
    @WsSecurity( role = ADMIN )
    public void delete( @WsParam( from = PATH ) String organizationId ) {
        organizationStorage.delete( organizationId );

        log.debug( "Organization [{}] deleted", organizationId );
    }

    @WsMethod( method = GET, path = "/{organizationId}/users" )
    @WsSecurity( role = ADMIN )
    @Override
    public List<User> users( @WsParam( from = PATH ) String organizationId ) {
        log.debug( "Fetching all users for organization [{}]", organizationId );

        return userStorage.select()
            .filter( user -> user.organizationId.equals( organizationId ) )
            .map( Converters::toUserDTO )
            .toList();
    }

    @WsMethod( method = POST, path = "/{organizationId}/users/store" )
    @WsSecurity( role = Role.USER )
    @WsValidate( { "validateOrganizationAccess", "validateUserAccess", "validateUserPrecedence", "validateUserCreationRole" } )
    @Override
    public User userStore( @WsParam( from = BODY ) User storeUser, @WsParam( from = PATH ) String organizationId,
                           @WsParam( from = SESSION ) User user ) {

        storeUser.password = Hash.sha256( salt, storeUser.password );
        userStorage.store( storeUser );

        log.debug( "New information about user " + storeUser.email + " was successfully added" );

        return Converters.toUserDTO( storeUser );
    }

    @WsMethod( method = GET, path = "/{organizationId}/users/{email}" )
    @WsSecurity( role = Role.USER )
    @WsValidate( { "validateOrganizationAccess", "validateUserAccessById" } )
    @Override
    public Optional<User> user( @WsParam( from = PATH ) String organizationId,
                                @WsParam( from = PATH ) String email,
                                @WsParam( from = SESSION ) User user ) {
        return userStorage.get( email ).map( Converters::toUserDTO );
    }

    @WsMethod( method = DELETE, path = "/{organizationId}/users/{email}/delete" )
    @WsSecurity( role = Role.ORGANIZATION_ADMIN )
    @WsValidate( { "validateOrganizationAccess", "validateUserAccessById" } )
    @Override
    public void userDelete( @WsParam( from = PATH ) String organizationId, @WsParam( from = PATH ) String email,
                            @WsParam( from = SESSION ) User user ) {
        userStorage.delete( email );

        log.debug( "User [{}] deleted", email );
    }

    @SuppressWarnings( "unused" )
    public ValidationErrors validateUserAccess( String organizationId, User storeUser ) {
        return validateUserAccessById( organizationId, storeUser.email );
    }

    @SuppressWarnings( "unused" )
    public ValidationErrors validateUserAccessById( String organizationId, String email ) {
        return OrganizationAwareWS.validateObjectAccess( userStorage.get( email ), organizationId );
    }

    @SuppressWarnings( "unused" )
    public ValidationErrors validateUserPrecedence( User user, User storeUser ) {
        return ( user.role != Role.ADMIN && storeUser.role.precedence < user.role.precedence )
            ? ValidationErrors.error( HTTP_FORBIDDEN, "Forbidden" ) : ValidationErrors.empty();
    }

    @SuppressWarnings( "unused" )
    public ValidationErrors validateUserCreationRole( User user, User storeUser ) {
        return ( user.role == Role.USER && !user.email.equals( storeUser.email ) )
            ? ValidationErrors.error( HTTP_FORBIDDEN, "Forbidden" ) : ValidationErrors.empty();
    }

}
