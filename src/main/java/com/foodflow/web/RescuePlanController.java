package com.foodflow.web;

import com.foodflow.agent.CoordinatorAgent;
import com.foodflow.model.RescuePlan;
import com.foodflow.store.RescuePlanStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Comparator;

@RestController
@RequestMapping("/api/rescue-plan")
public class RescuePlanController {

    private final RescuePlanStore rescuePlanStore;
    private final CoordinatorAgent coordinatorAgent;

    public RescuePlanController(RescuePlanStore rescuePlanStore, CoordinatorAgent coordinatorAgent) {
        this.rescuePlanStore = rescuePlanStore;
        this.coordinatorAgent = coordinatorAgent;
    }

    @GetMapping
    public ResponseEntity<RescuePlan> latest() {
        return rescuePlanStore.findLatest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/all")
    public List<RescuePlan> all() {
        return rescuePlanStore.findAll().stream()
                .sorted(Comparator.comparing(RescuePlan::getCreatedAt).reversed())
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RescuePlan> byId(@PathVariable String id) {
        return rescuePlanStore.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<RescuePlan> confirm(@PathVariable String id) {
        return coordinatorAgent.confirm(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
