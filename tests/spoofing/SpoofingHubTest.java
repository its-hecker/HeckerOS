/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.hecker;

import com.android.internal.org.bouncycastle.asn1.x500.X500Name;
import com.android.internal.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import com.android.internal.org.bouncycastle.cert.X509v3CertificateBuilder;
import com.android.internal.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.json.*;

/** Uses ephemeral test CAs. No public provider keybox, private key fixture or live network. */
public final class SpoofingHubTest {
    private static int checks;
    private static final long NOW = 1792886400000L, DAY = 86400000L;
    private interface Work { void run() throws Exception; }
    private static void check(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    private static void rejected(Work work, String name) throws Exception {
        try { work.run(); throw new AssertionError(name); }
        catch (IllegalArgumentException | IOException expected) { checks++; }
    }
    private static KeyPair keys(String algorithm) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        if (algorithm.equals("EC")) generator.initialize(new ECGenParameterSpec("secp256r1"));
        else generator.initialize(2048);
        return generator.generateKeyPair();
    }
    private static X509Certificate cert(KeyPair subject, KeyPair issuer, String subjectName,
            String issuerName, int serial, long end) throws Exception {
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(new X500Name(issuerName),
                BigInteger.valueOf(serial), new Date(NOW-DAY), new Date(end),
                new X500Name(subjectName), SubjectPublicKeyInfo.getInstance(subject.getPublic().getEncoded()));
        byte[] der = builder.build(new JcaContentSignerBuilder(issuer.getPrivate().getAlgorithm().equals("EC")
                ? "SHA256withECDSA" : "SHA256withRSA").build(issuer.getPrivate())).getEncoded();
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }
    private static String pem(X509Certificate certificate) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'})
                .encodeToString(certificate.getEncoded())+"\n-----END CERTIFICATE-----";
    }
    private static String build(String id, String rc, boolean canary, String month) throws Exception {
        return new JSONObject().put("buildId",id).put("releaseCandidateName",rc)
                .put("previewMetadata",new JSONObject().put("canary",canary).put("id",month)).toString();
    }
    public static void main(String[] arguments) throws Exception {
        var modes = SpoofingTargets.parse("# comment\ncom.test.auto\ncom.test.leaf?\ncom.test.cert!\ncom.test.skip-\n");
        check(modes.size()==4,"four text target modes");
        check(modes.get("com.test.skip")==SpoofingTargets.Mode.SKIP,"Skip suffix survives import");
        check(SpoofingTargets.parse(SpoofingTargets.json(modes)).equals(modes),"JSON round trip preserves modes");
        check(SpoofingTargets.parse("[]").isEmpty(),"explicit empty list remains empty");
        check(SpoofingTargets.parse("[{\"package\":\"com.test.alias\",\"mode\":\"CERT_GEN\"}]")
                .get("com.test.alias")==SpoofingTargets.Mode.GENERATE,"upstream Cert alias");
        check(SpoofingTargets.parse("android").containsKey("android"),"protected imports remain editable");
        rejected(()->SpoofingTargets.parse("com.test.bad;rm"),"invalid target rejected");
        rejected(()->SpoofingTargets.parse("[{\"package\":\"com.test.a\",\"mode\":\"bogus\"}]"),"unknown mode rejected");
        StringBuilder many = new StringBuilder();
        for(int i=0;i<513;i++)many.append("com.test.p").append(i).append('\n');
        rejected(()->SpoofingTargets.parse(many.toString()),"target count bounded");
        rejected(()->SpoofingTargets.parse("x".repeat(256*1024+1)),"target bytes bounded");

        Map<String,String> devices = PixelCanaryProfileFetcher.parseDevices(
                "<tr id=\"future_beta\"><td>Pixel 20 <b>Pro</b></td></tr>"
                +"<tr id='tablet'><td>Pixel Tablet</td></tr><tr id='other'><td>Other phone</td></tr>");
        check(devices.equals(Map.of("future","Pixel 20 Pro","tablet","Pixel Tablet")),"device discovery supports future Pixel models");
        String builds = "{\"flashstationBuild\":["+build("9","ZP11.261018.001",true,"canary-202610")+","
                +build("20","ZP11.261018.002",true,"canary-202610")+","
                +build("999","ZP11.261018.999",false,"stable")+"]}";
        Map<String,String> profile = PixelCanaryProfileFetcher.fromBuilds(builds,"future","Pixel 20 Pro");
        check(profile.get("FINGERPRINT").endsWith("/20:user/release-keys"),"highest numeric Canary build selected");
        check(profile.get("SECURITY_PATCH").equals("2026-10-05"),"Canary patch month mapped");
        check(profile.get("MODEL").equals("Pixel 20 Pro"),"actual selected device retained");
        check(SpoofingData.parseProfile(new JSONObject(profile).toString()).equals(profile),"profile JSON import");
        check(SpoofingData.parseProfile("FINGERPRINT="+profile.get("FINGERPRINT")+"\nspoofBuild=0")
                .get("spoofBuild").equals("0"),"property import retains explicit advanced switch");
        rejected(()->PixelCanaryProfileFetcher.fromBuilds("{\"flashstationBuild\":[]}","future","Pixel 20"),"no sample fallback for missing metadata");
        rejected(()->PixelCanaryProfileFetcher.fromBuilds("{}","future","Pixel 20"),"wrong Google schema rejected");
        rejected(()->SpoofingData.parseProfile("{\"FINGERPRINT\":\"broken\"}"),"invalid profile rejected before saving");
        rejected(()->SpoofingData.parseProfile("x".repeat(65537)),"profile size bounded");
        for(String allowed:List.of(SpoofingHttp.ROOTS,SpoofingHttp.REVOCATION,SpoofingHttp.KEYBOX,
                "https://content-flashstation-pa.googleapis.com/v1/builds?product=future_beta"))
            check(SpoofingHttp.allowed(new URL(allowed)),"public source accepted");
        for(String denied:List.of("http://android.googleapis.com/attestation/root",
                "https://user@android.googleapis.com/attestation/root","https://android.googleapis.com:8443/attestation/root",
                "https://example.com/attestation/root","https://gitea.com/other/keybox.xml",
                "https://android.googleapis.com/anything","https://content-flashstation-pa.googleapis.com/other"))
            check(!SpoofingHttp.allowed(new URL(denied)),"unexpected source refused");
        check(SpoofingMaintenance.due(0,NOW),"first refresh due");
        check(!SpoofingMaintenance.due(NOW-1000,NOW),"daily refresh throttled");
        check(SpoofingMaintenance.due(NOW+1,NOW),"clock rollback handled");
        check(SpoofingMaintenance.due(NOW-DAY,NOW),"daily refresh becomes due");

        for(String algorithm:List.of("EC","RSA")) {
            KeyPair rootKeys=keys(algorithm),leafKeys=keys(algorithm);
            X509Certificate root=cert(rootKeys,rootKeys,"CN=Test root","CN=Test root",257,NOW+90*DAY);
            X509Certificate leaf=cert(leafKeys,rootKeys,"CN=Test leaf","CN=Test root",42,NOW+30*DAY);
            List<List<X509Certificate>> chains=List.of(List.of(leaf,root));
            String anchors=new JSONArray().put(pem(root)).toString(), clean="{\"entries\":{}}";
            check(KeyboxStatus.evaluate(chains,anchors,clean,NOW).state==KeyboxStatus.State.VALID,algorithm+" trusted live certificate check");
            check(KeyboxStatus.evaluate(chains,anchors,null,NOW).state==KeyboxStatus.State.UNKNOWN,"offline never claims validity");
            check(KeyboxStatus.evaluate(chains,anchors,"{}",NOW).state==KeyboxStatus.State.UNKNOWN,"malformed revocation never claims validity");
            check(KeyboxStatus.evaluate(chains,anchors,"{\"entries\":{\"2a\":{\"status\":\"REVOKED\"}}}",NOW).state==KeyboxStatus.State.REVOKED,"revoked leaf");
            check(KeyboxStatus.evaluate(chains,anchors,"{\"entries\":{\"101\":{\"status\":\"SUSPENDED\"}}}",NOW).state==KeyboxStatus.State.SUSPENDED,"suspended chain certificate");
            check(KeyboxStatus.evaluate(chains,null,clean,NOW).state==KeyboxStatus.State.UNTRUSTED,"unknown root refused");
            check(KeyboxStatus.evaluate(List.of(List.of(root,leaf)),anchors,clean,NOW).state==KeyboxStatus.State.INVALID,"invalid chain order refused");
            X509Certificate soon=cert(leafKeys,rootKeys,"CN=Test leaf","CN=Test root",43,NOW+DAY);
            check(KeyboxStatus.evaluate(List.of(List.of(soon,root)),anchors,clean,NOW).state==KeyboxStatus.State.EXPIRING,"expiry warning");
            check(KeyboxStatus.evaluate(List.of(List.of(soon,root)),anchors,clean,NOW+2*DAY).state==KeyboxStatus.State.EXPIRED,"expired certificate");
            String xml="<AndroidAttestation><Key><CertificateChain><Certificate>"+pem(leaf)
                    +"</Certificate><Certificate>"+pem(root)+"</Certificate></CertificateChain></Key></AndroidAttestation>";
            check(KeyboxStatus.chains(KeyboxStatus.xml(xml)).get(0).size()==2,"certificate XML parsed");
            check(KeyboxStatus.xml(Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8))).equals(xml),"base64 keybox import");
        }
        rejected(()->KeyboxStatus.xml("<!DOCTYPE Key [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><Key>&a;</Key>"),"external entity rejected");
        rejected(()->KeyboxStatus.xml("<"+"x".repeat(256*1024)),"keybox bytes bounded");
        check(KeyboxStatus.check("",false).state==KeyboxStatus.State.MISSING,"absent keybox reported");
        check(KeyboxStatus.check("<bad/>",false).state==KeyboxStatus.State.INVALID,"invalid XML rejected");
        System.out.println(checks+" Spoofing hub import, discovery, network and certificate checks: PASS");
    }
}
