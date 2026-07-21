package com.skillsphere.learning.client;

import com.skillsphere.learning.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class SkillServiceClient {

    private final RestClient restClient;
    private final String skillServiceUrl;

    public SkillServiceClient(RestClient restClient,
                              @Value("${skill.service.url:http://localhost:8081/api/v1}") String skillServiceUrl) {
        this.restClient = restClient;
        this.skillServiceUrl = skillServiceUrl;
    }

    public boolean employeeExists(UUID employeeId) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null || attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION) == null) {
            throw new ServiceUnavailableException("No authentication token found in request context.");
        }

        try {
            ResponseEntity<Void> response = restClient.get()
                    .uri(skillServiceUrl + "/employees/" + employeeId)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new ServiceUnavailableException("Authentication failed when calling Skill Service.", e);
            }
            throw new ServiceUnavailableException("Error response from Skill Service: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ServiceUnavailableException("Skill Service is currently unreachable: " + e.getMessage(), e);
        }
    }
}
