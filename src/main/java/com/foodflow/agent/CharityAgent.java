package com.foodflow.agent;

import com.foodflow.model.CharityRequest;
import org.springframework.stereotype.Component;

/** Represents the charity's side of the negotiation: quantity, dietary needs and transport status. */
@Component
public class CharityAgent {

    public String describe(CharityRequest charity) {
        String dietary = charity.getAcceptedDietaryTypes() == null || charity.getAcceptedDietaryTypes().isEmpty()
                ? "" : String.join(", ", charity.getAcceptedDietaryTypes()) + " ";
        StringBuilder sb = new StringBuilder();
        sb.append(charity.getRequestedQuantity()).append(" ").append(dietary).append("meals are required");
        if (charity.getLatestDeliveryTime() != null) {
            sb.append(" before ").append(charity.getLatestDeliveryTime());
        }
        sb.append(".");
        if (!charity.isCanCollect()) {
            sb.append("\nTransport is needed.");
        }
        return sb.toString();
    }

    public String acceptOrRejectAllocation(boolean approved, CharityRequest charity) {
        return approved
                ? charity.getCharityName() + " accepts the proposed allocation."
                : charity.getCharityName() + " cannot accept this allocation.";
    }
}
