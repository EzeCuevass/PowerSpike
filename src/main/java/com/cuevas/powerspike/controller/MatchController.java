package com.cuevas.powerspike.controller;

import com.cuevas.powerspike.dto.MatchSummaryDTO;
import com.cuevas.powerspike.service.MatchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/{gameName}/{tagLine}")
    public List<MatchSummaryDTO> getMatchHistory(
            @PathVariable String gameName,
            @PathVariable String tagLine,
            @RequestParam(defaultValue = "20") int count) {
        return matchService.getMatchHistory(gameName, tagLine, count);
    }
}
