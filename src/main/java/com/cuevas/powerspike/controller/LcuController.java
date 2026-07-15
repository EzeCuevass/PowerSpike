package com.cuevas.powerspike.controller;

import com.cuevas.powerspike.dto.LcuChampSelectDTO;
import com.cuevas.powerspike.service.LcuApi;
import com.cuevas.powerspike.service.LcuLockfile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@RestController
@RequestMapping("/api/lcu")
public class LcuController {

    private final LcuApi lcuApi;
    private final LcuLockfile lcuLockfile;
    private final RestTemplate lcuRestTemplate;

    public LcuController(LcuApi lcuApi, LcuLockfile lcuLockfile,
                         @org.springframework.beans.factory.annotation.Qualifier("lcuRestTemplate") RestTemplate lcuRestTemplate) {
        this.lcuApi = lcuApi;
        this.lcuLockfile = lcuLockfile;
        this.lcuRestTemplate = lcuRestTemplate;
    }

    @GetMapping("/phase")
    public String getPhase() {
        return lcuApi.getCurrentPhase();
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        String phase = lcuApi.getCurrentPhase();
        if (phase == null) phase = "CLOSED";
        if (lcuLockfile.isConnected()) {
            return Map.of(
                "connected", true,
                "phase", phase,
                "port", lcuLockfile.getPort()
            );
        }
        return Map.of(
            "connected", false,
            "phase", phase
        );
    }

    @GetMapping("/champ-select")
    public LcuChampSelectDTO getChampSelect() {
        return lcuApi.getCurrentChampSelect();
    }

    @GetMapping("/refresh")
    public Map<String, Object> refresh() {
        String result = lcuApi.refreshPhase();
        return Map.of(
            "connected", lcuLockfile.isConnected(),
            "phase", lcuApi.getCurrentPhase(),
            "debug", result
        );
    }

    @GetMapping("/debug")
    public Map<String, Object> debug() {
        return Map.of(
            "connected", lcuLockfile.isConnected(),
            "port", lcuLockfile.isConnected() ? lcuLockfile.getPort() : null,
            "phase", lcuApi.getCurrentPhase(),
            "hasChampSelect", lcuApi.getCurrentChampSelect() != null,
            "champSelectError", lcuApi.getChampSelectError()
        );
    }

    @GetMapping("/refresh/champ-select")
    public Map<String, Object> refreshChampSelect() {
        String result = lcuApi.refreshChampSelect();
        return Map.of(
            "result", result,
            "phase", lcuApi.getCurrentPhase(),
            "hasChampSelect", lcuApi.getCurrentChampSelect() != null
        );
    }

    @GetMapping("/raw/champ-select")
    public String rawChampSelect() {
        if (!lcuLockfile.isConnected()) return "No conectado";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", lcuLockfile.getAuth());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = "https://127.0.0.1:" + lcuLockfile.getPort() + "/lol-champ-select/v1/session";
        try {
            return lcuRestTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
