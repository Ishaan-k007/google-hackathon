package com.foodflow.web;

import com.foodflow.agent.CoordinatorAgent;
import com.foodflow.agent.NegotiationOutcome;
import com.foodflow.service.DemoDataService;
import com.foodflow.service.IntakeService;
import com.foodflow.store.AgentMessageStore;
import com.foodflow.database.CharityRequestDatabase;
import com.foodflow.store.DonationStore;
import com.foodflow.store.DriverStore;
import com.foodflow.store.RescuePlanStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoDataService demoDataService;
    private final IntakeService intakeService;
    private final CoordinatorAgent coordinatorAgent;
    private final DonationStore donationStore;
    private final CharityRequestDatabase charityRequestDatabase;
    private final DriverStore driverStore;
    private final RescuePlanStore rescuePlanStore;
    private final AgentMessageStore agentMessageStore;

    public DemoController(DemoDataService demoDataService, IntakeService intakeService,
                           CoordinatorAgent coordinatorAgent, DonationStore donationStore,
                           CharityRequestDatabase charityRequestDatabase, DriverStore driverStore,
                           RescuePlanStore rescuePlanStore, AgentMessageStore agentMessageStore) {
        this.demoDataService = demoDataService;
        this.intakeService = intakeService;
        this.coordinatorAgent = coordinatorAgent;
        this.donationStore = donationStore;
        this.charityRequestDatabase = charityRequestDatabase;
        this.driverStore = driverStore;
        this.rescuePlanStore = rescuePlanStore;
        this.agentMessageStore = agentMessageStore;
    }

    @PostMapping("/load")
    public Object load(@RequestParam String role) {
        return switch (role) {
            case "supermarket" -> intakeService.intake(demoDataService.goodDonation());
            case "charity" -> intakeService.intake(demoDataService.goodCharityRequest());
            case "driver" -> intakeService.intake(demoDataService.goodDriver());
            default -> Map.of("error", "Unknown role: " + role);
        };
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        donationStore.clear();
        charityRequestDatabase.clear();
        driverStore.clear();
        rescuePlanStore.clear();
        agentMessageStore.clear();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "reset");
        return response;
    }

    @PostMapping("/unsafe")
    public Map<String, Object> unsafe() {
        donationStore.clear();
        charityRequestDatabase.clear();
        driverStore.clear();
        rescuePlanStore.clear();
        agentMessageStore.clear();

        intakeService.intake(demoDataService.unsafeDonation());
        intakeService.intake(demoDataService.unsafeCharityRequest());
        intakeService.intake(demoDataService.unsafeDriver());

        NegotiationOutcome outcome = coordinatorAgent.runNegotiation();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hadCandidate", outcome.hadCandidate);
        response.put("plan", outcome.plan);
        return response;
    }
}
