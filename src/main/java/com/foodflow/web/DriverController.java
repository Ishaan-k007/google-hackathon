package com.foodflow.web;

import com.foodflow.model.DriverAvailability;
import com.foodflow.service.IntakeService;
import com.foodflow.store.DriverStore;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverStore driverStore;
    private final IntakeService intakeService;

    public DriverController(DriverStore driverStore, IntakeService intakeService) {
        this.driverStore = driverStore;
        this.intakeService = intakeService;
    }

    @PostMapping
    public DriverAvailability create(@RequestBody DriverAvailability driver) {
        return intakeService.intake(driver);
    }

    @GetMapping
    public List<DriverAvailability> list() {
        return driverStore.findAll().stream()
                .sorted(Comparator.comparing(DriverAvailability::getCreatedAt).reversed())
                .toList();
    }
}
