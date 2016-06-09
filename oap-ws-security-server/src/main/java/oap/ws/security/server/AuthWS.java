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
import oap.http.HttpResponse;
import oap.ws.WsMethod;
import oap.ws.WsParam;
import oap.ws.security.Role;
import oap.ws.security.Token;
import oap.ws.security.User;
import oap.ws.security.client.WsSecurity;

import java.util.Optional;

import static oap.http.Request.HttpMethod.GET;
import static oap.ws.WsParam.From.PATH;
import static oap.ws.WsParam.From.SESSION;

@Slf4j
public class AuthWS {

    private final AuthService authService;

    public AuthWS( AuthService authService ) {
        this.authService = authService;
    }

    @WsMethod( method = GET, path = "/{tokenId}" )
    @WsSecurity( role = Role.USER )
    public HttpResponse getToken( @WsParam( from = PATH ) String tokenId,
                                  @WsParam( from = SESSION ) User user ) {
        final Optional<Token> tokenOptional = authService.getToken( tokenId );

        if ( tokenOptional.isPresent() ) {
            final Token token = tokenOptional.get();

            if ( Role.ADMIN.equals( user.role ) || token.user.email.equals( user.email ) ) {
                return HttpResponse.ok( token );
            } else {
                final HttpResponse httpResponse = HttpResponse.status( 403, "User " + user.email + " " +
                        "cannot view requested token" );

                log.debug( httpResponse.reasonPhrase );

                return httpResponse;
            }
        } else {
            return HttpResponse.NOT_FOUND;
        }
    }
}
