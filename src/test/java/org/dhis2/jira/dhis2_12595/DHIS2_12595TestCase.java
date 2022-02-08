package org.dhis2.jira.dhis2_12595;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;

@Testcontainers
public class DHIS2_12595TestCase
{
    private static String orgUnitId;

    @Container
    public static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>(
        DockerImageName.parse( "postgis/postgis:12-3.2-alpine" ).asCompatibleSubstituteFor( "postgres" ) )
        .withDatabaseName( "dhis2" )
        .withNetworkAliases( "db" )
        .withUsername( "dhis" )
        .withPassword( "dhis" ).withNetwork( Network.newNetwork() );

    @Container
    public static final GenericContainer<?> DHIS2_CONTAINER = new GenericContainer<>( "dhis2/core:2.37.2" )
        .dependsOn( POSTGRESQL_CONTAINER )
        .withClasspathResourceMapping( "dhis.conf", "/DHIS2_home/dhis.conf", BindMode.READ_WRITE )
        .withNetwork( POSTGRESQL_CONTAINER.getNetwork() ).withExposedPorts( 8080 )
        .waitingFor( new HttpWaitStrategy().forStatusCode( 200 ) )
        .withEnv( "WAIT_FOR_DB_CONTAINER", "db" + ":" + 5432 + " -t 0" );

    @BeforeAll
    public static void beforeAll()
    {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.baseURI = "http://" + DHIS2_CONTAINER.getHost() + ":" + DHIS2_CONTAINER.getFirstMappedPort();
        RestAssured.requestSpecification = new RequestSpecBuilder().build().contentType( ContentType.JSON ).auth()
            .preemptive()
            .basic( "admin", "district" );

        orgUnitId = createOrgUnit();
        addOrgUnitToUser( orgUnitId );
        createOrgUnitLevel();
    }

    private static void addOrgUnitToUser( String orgUnitId )
    {
        when().post( "api/users/M5zQapPyTZI/organisationUnits/{organisationUnitId}", orgUnitId ).then()
            .statusCode( 204 );
    }

    private static void createOrgUnitLevel()
    {
        Map<String, List<Map<String, ? extends Serializable>>> orgUnitLevels = Map.of(
            "organisationUnitLevels", List.of( Map.of( "name", "Level 1", "level", 1 ) ) );
        given().body( orgUnitLevels ).when().post( "api/filledOrganisationUnitLevels" ).then().statusCode( 201 );
    }

    private static String createOrgUnit()
    {
        Map<String, ? extends Serializable> orgUnit = Map.of( "name", "Acme",
            "shortName", "Acme",
            "openingDate", new Date().getTime() );
        return given().body( orgUnit ).when().post( "api/organisationUnits" ).then().statusCode( 201 ).extract()
            .path( "response.uid" );
    }

    private static void importMetaData()
        throws IOException
    {
        String metaData = new String(
            Thread.currentThread().getContextClassLoader().getResourceAsStream( "MLAG00_1.2.1_DHIS2.37.json" )
                .readAllBytes(),
            Charset.defaultCharset() );
        given().queryParam( "atomicMode", "NONE" ).body( metaData ).when().post( "api/metadata" ).then()
            .statusCode( 200 );
    }

    @Test
    public void test()
        throws IOException
    {
        importMetaData();
    }
}
