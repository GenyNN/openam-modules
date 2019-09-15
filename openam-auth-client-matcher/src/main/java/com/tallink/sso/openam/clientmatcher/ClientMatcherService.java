package com.tallink.sso.openam.clientmatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.identity.authentication.spi.AuthLoginException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class ClientMatcherService {

    private String urlRequest;

    public final static String EMAIL = "email";

    public final static String PHONE = "phoneNumber";

    public final static String COUNTRY_CODE = "countryCode";

    public final static String CLUB_ONE = "clubOne";

    public final static String FIRST_NAME = "firstName";

    public final static String LAST_NAME = "lastName";

    public ClientMatcherService(String url) {
        this.urlRequest = url;
    }

    public Map<String, Object> tryFindBestMatch(String[] rawParams) throws AuthLoginException, IOException{
        String request = "?";
        if (rawParams.length % 2 != 0)
            return null;
        List<String> params = IntStream.range(0, rawParams.length/2)
                        .mapToObj(i -> rawParams[i*2]+"="+rawParams[i*2+1]).collect(Collectors.toList());
        String joinedParams = String.join("&", params);
        String response = doGet(request+joinedParams);
        return getBestMatch(response);
    }

    private Map<String, Object> getBestMatch(String responseJson) throws AuthLoginException, IOException {

        ObjectMapper mapper = new ObjectMapper();

        List<Map<String, Object>> response = mapper.readValue(responseJson, new TypeReference<List<Map<String, Object>>>(){} );
        if(response.size() > 1)
            throw new AuthLoginException("Client Matcher: Multiple Best Match");
        if (response.size() == 0)
            return null;
        if (response.size() == 1)
            return response.get(0);

        return null;
    }

    private String doGet(String requestParams) throws IOException {

        String resRequest = urlRequest+requestParams;
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(resRequest);
        HttpResponse response = client.execute(request);
        return EntityUtils.toString(response.getEntity());

    }
}
