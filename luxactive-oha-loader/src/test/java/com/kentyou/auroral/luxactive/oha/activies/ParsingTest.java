/*
 * Copyright 2025 Kentyou
 * Proprietary and confidential
 *
 * All Rights Reserved.
 * Unauthorized copying of this file is strictly prohibited
 */

package com.kentyou.auroral.luxactive.oha.activies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kentyou.auroral.luxactive.oha.activies.dto.ActivityDescriptionDTO;

public class ParsingTest {

    /**
     * Reads a file from the test folder
     *
     * @param filename File name
     * @return File content as string
     * @throws Exception Error reading file
     */
    String readFile(final String filename) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/data/" + filename)) {
            if (inputStream == null) {
                throw new FileNotFoundException(filename);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void testParsing() throws Exception {
        final String strContent = readFile("oha-activities-halogaland.json");

        final ActivityLoader loader = new ActivityLoader();
        final List<ActivityDescriptionDTO> activities = loader.mapper.readValue(strContent,
                new TypeReference<List<ActivityDescriptionDTO>>() {
                });
        assertNotNull(activities);
        assertEquals(24, activities.size());

        ActivityDescriptionDTO activity = activities.get(0);
        // Main description
        assertEquals("https://auroralh2020.github.io/auroral-ontology-contexts/tour/context.json",
                activity.jsonContext);
        assertEquals("d57b29de-a5ff-4f65-8b37-4467830749df", activity.identifier);
        assertEquals("", activity.hiddenDescription);
        assertEquals(
                "<p><strong>Beryktet</strong><br>Tore Hund vil alltid være forbundet med St. Olav på grunn av hendelsene på Stiklestad i 1030 og alt som skjedde etterpå. Han blir sett på som drapsmann av en helgen, og det er vanskelig å riste av seg det ryktet. Men denne høvdingen fra Bjarkøy var verken en skruppelløs skurk som brutalt drepte den norske helgenkongen, eller en motstandsmann som kjempet mot den urettferdige nasjonale overmakten som en separatist. Han var en del av et norsk stormannsforbund som kjempet i en konflikt som endte med at deres politiske motstander ble drept, slik det var vanlig på den tiden. Tore Hund sin posisjon i dette forbundet viser hvor tett knyttet det nordligste høvdingdømmet var til rikspolitikken i det norske riket.&nbsp;</p><p>&nbsp;<strong>Samarbeid</strong><br>Tores Hunds rolle i historien har på mange måter gjort ham til et symbol for de mange prosessene som utspilte seg i hans tid. Og det er her det fremtidige fokuset bør ligge. Ikke på personen Tore Hund, men på betydningen av Bjarkøys rike historie gjennom mer enn 1000 år. Her, på et høvdingsete med en historie som går tilbake til 300-tallet, samlet folk kunnskap og samarbeidet med ulike grupper lokalt, nordøst- og sørover gjennom århundrene. Særlig samarbeidet man med den samiske befolkningen og la grunnlaget for et av de mest velstående høvdingdømmene i det norske riket. Etter Tore Hund ble høvdingsetet hjemmet til en av høymiddelalderens mektigste og mest innflytelsesrike adelsslekter. Bjarkøy sto derfor verken imot eller utenfor den utviklingen som hele Norge opplevde, fra lokale høvdingdømmer til et rikskongedømme, men var en del av den.&nbsp;</p>",
                activity.longDescription);
        assertEquals("", activity.longDescriptionNotSearched);
        assertEquals(
                "<p><strong>Tore Hund vil alltid være forbundet med St. Olav på grunn av hendelsene på Stiklestad i 1030 og alt som skjedde etterpå.</strong></p>",
                activity.shortDescription);
        assertFalse(activity.bookable);
        assertNull(activity.externalBookingUrl);
        assertEquals("Tore Hunds rolle i historien", activity.title);
        assertEquals(OffsetDateTime.of(2023, 6, 13, 10, 23, 03, 138050000, ZoneOffset.UTC).toInstant(),
                activity.lastModified);
        assertEquals(
                "<p>Kilde: Teksten er et utdrag fra forskningsartikkelen <i>Her skal du få kjenne en bjarkøying! Om Tore Hunds ettermæle</i> utarbeidet av Stefan Figenschow, UiT, Norges Arktiske Universitet, 2020. Artikkelen er også utgitt i Håløygminne, februar 2022.<br><span style=\"color:hsl(0,0%,0%);\">Bilde 1: Maleri av Tore Hund på Trondenes Historiske senter. FOTO: BØRGE MADSEN, SØR-TROMS MUSEUM</span><br><span style=\"color:hsl(0,0%,0%);\">Bilde 2: </span><span style=\"color:rgb(0,0,0);\">Tegningen er basert på illustrasjonene fra den populære folkeutgaven av \"Heimskringla.\"</span></p>",
                activity.right);
        assertEquals("no", activity.language);
        assertEquals("hidden_poi", activity.mapPointCategory);

        // Price
        assertNotNull(activity.price);
        assertNull(activity.price.amount);
        assertEquals("euro_cent", activity.price.currency);

        // Location
        assertNotNull(activity.location);
        assertEquals(68.41728, activity.location.latitude, 0.000001);
        assertEquals(17.90582, activity.location.longitude, 0.000001);
        assertNull(activity.location.altitude);

        // Images
        assertEquals("https://asset.myoha.at/file/8cf2af6e-0fdb-4844-a162-7ffd693027cb.png", activity.coverImage);
        assertNotNull(activity.images);
        assertEquals(2, activity.images.size());
        assertEquals("https://asset.myoha.at/file/1ad2a9d7-dce7-4578-b3be-8ff130019752.png", activity.images.get(0));
        assertEquals("https://asset.myoha.at/file/a36a57a9-7fb9-4ffc-aeb5-6c921c106d9a.png", activity.images.get(1));

        // Channel
        assertNotNull(activity.channel);
        assertEquals("6d4e5a5f-7562-498c-a503-b98af7dfd3ae", activity.channel.id);
        assertEquals("Activities", activity.channel.name);

        // Null tour and event
        assertNull(activity.event);
        assertNull(activity.tour);

        // Look for a valid tour
        activity = activities.get(11);
        assertNotNull(activity);
        assertNotNull(activity.tour);
        assertEquals(3, activity.tour.difficulty);
        assertNotNull(activity.tour.geoPolygon);
        assertEquals(26, activity.tour.geoPolygon.size());
        assertEquals(2, activity.tour.geoPolygon.get(0).size());
        assertEquals(68.42742, activity.tour.geoPolygon.get(0).get(0), 0.000001);
        assertEquals(17.443818, activity.tour.geoPolygon.get(0).get(1), 0.000001);
    }
}
