package com.example.fooddonation.controller;

import com.example.fooddonation.entity.DonationDTO;
import com.example.fooddonation.entity.DonationStatus;
import com.example.fooddonation.repository.DonationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/donation/status")
@CrossOrigin(origins = "http://localhost:3000")
public class DonationStatusController {

    @Autowired
    private DonationRepository donationRepository;

    // Get all donations for a donor with status
    @GetMapping("/donor/{donorId}")
    public ResponseEntity<?> getDonorDonations(@PathVariable int donorId) {
        try {
            List<DonationDTO> donations = donationRepository.findByDonorIdOrderByDonatedDateDesc(donorId);
            return ResponseEntity.ok(donations);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get all donations for an NGO (for NGO dashboard)
    @GetMapping("/ngo/{ngoId}")
    public ResponseEntity<?> getNgoDonations(@PathVariable int ngoId) {
        try {
            List<DonationDTO> donations = donationRepository.findByNgoIdOrderByDonatedDateDesc(ngoId);
            return ResponseEntity.ok(donations);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get donations by status (for NGO to see pending pickups, etc.)
    @GetMapping("/ngo/{ngoId}/status/{status}")
    public ResponseEntity<?> getNgoDonationsByStatus(
            @PathVariable int ngoId,
            @PathVariable String status
    ) {
        try {
            DonationStatus donationStatus = DonationStatus.valueOf(status.toUpperCase());
            List<DonationDTO> donations = donationRepository.findByNgoIdAndStatus(ngoId, donationStatus);
            return ResponseEntity.ok(donations);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get single donation with full details
    @GetMapping("/{donationId}")
    public ResponseEntity<?> getDonationDetails(@PathVariable int donationId) {
        try {
            DonationDTO donation = donationRepository.findById(donationId)
                    .orElseThrow(() -> new RuntimeException("Donation not found"));
            return ResponseEntity.ok(donation);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Update donation status (Main endpoint)
    @PutMapping("/update/{donationId}")
    public ResponseEntity<?> updateStatus(
            @PathVariable int donationId,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            DonationDTO donation = donationRepository.findById(donationId)
                    .orElseThrow(() -> new RuntimeException("Donation not found"));

            String newStatusStr = (String) payload.get("status");
            DonationStatus newStatus = DonationStatus.valueOf(newStatusStr.toUpperCase());

            // Validate status progression (can't go backwards)
            if (donation.getStatus() != null && newStatus.isBefore(donation.getStatus())) {
                throw new RuntimeException("Cannot move status backwards");
            }

            LocalDateTime now = LocalDateTime.now();

            // Update status and set timestamp
            donation.setStatus(newStatus);

            switch (newStatus) {
                case CONFIRMED:
                    donation.setConfirmedAt(now);
                    donation.setStatusMessage("Donation confirmed. NGO will contact you soon.");
                    break;

                case SCHEDULED:
                    donation.setScheduledAt(now);
                    // Get scheduled date from payload
                    String scheduledDateStr = (String) payload.get("pickupScheduledDate");
                    if (scheduledDateStr != null) {
                        donation.setPickupScheduledDate(LocalDateTime.parse(scheduledDateStr));
                    }
                    String instructions = (String) payload.get("specialInstructions");
                    if (instructions != null) {
                        donation.setSpecialInstructions(instructions);
                    }
                    donation.setStatusMessage("Pickup scheduled successfully.");
                    break;

                case PICKED_UP:
                    donation.setPickedUpAt(now);
                    donation.setStatusMessage("Donation picked up from donor.");
                    break;

                case IN_TRANSIT:
                    donation.setInTransitAt(now);
                    donation.setStatusMessage("Donation is being transported to distribution center.");
                    break;

                case DELIVERED:
                    donation.setDeliveredAt(now);
                    Integer beneficiariesCount = (Integer) payload.get("beneficiariesCount");
                    if (beneficiariesCount != null) {
                        donation.setBeneficiariesCount(beneficiariesCount);
                    }
                    donation.setStatusMessage("Donation delivered to beneficiaries.");
                    break;

                case COMPLETED:
                    donation.setCompletedAt(now);
                    String impactDesc = (String) payload.get("impactDescription");
                    if (impactDesc != null) {
                        donation.setImpactDescription(impactDesc);
                    }
                    donation.setStatusMessage("Donation process completed. Thank you!");
                    break;
            }

            // Add NGO notes if provided
            String ngoNotes = (String) payload.get("ngoNotes");
            if (ngoNotes != null && !ngoNotes.trim().isEmpty()) {
                donation.setNgoNotes(ngoNotes);
            }

            // Track who updated
            String updatedBy = (String) payload.get("updatedBy");
            if (updatedBy != null) {
                donation.setUpdatedBy(updatedBy);
            }

            // Custom message override
            String customMessage = (String) payload.get("statusMessage");
            if (customMessage != null && !customMessage.trim().isEmpty()) {
                donation.setStatusMessage(customMessage);
            }

            donationRepository.save(donation);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("donation", donation);
            response.put("message", "Status updated to " + newStatus.getDisplayName());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + e.getMessage());
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Schedule pickup (specific endpoint for NGO)
    @PutMapping("/schedule-pickup/{donationId}")
    public ResponseEntity<?> schedulePickup(
            @PathVariable int donationId,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            DonationDTO donation = donationRepository.findById(donationId)
                    .orElseThrow(() -> new RuntimeException("Donation not found"));

            String pickupDateStr = (String) payload.get("pickupDate");
            String pickupAddress = (String) payload.get("pickupAddress");
            String instructions = (String) payload.get("specialInstructions");
            String ngoNotes = (String) payload.get("ngoNotes");

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime pickupDate = LocalDateTime.parse(pickupDateStr);

            donation.setStatus(DonationStatus.SCHEDULED);
            donation.setScheduledAt(now);
            donation.setPickupScheduledDate(pickupDate);
            
            if (pickupAddress != null) {
                donation.setPickupAddress(pickupAddress);
            }
            if (instructions != null) {
                donation.setSpecialInstructions(instructions);
            }
            if (ngoNotes != null) {
                donation.setNgoNotes(ngoNotes);
            }

            donation.setStatusMessage("Pickup scheduled for " + pickupDate.toString());
            donation.setUpdatedBy("NGO");

            donationRepository.save(donation);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("donation", donation);
            response.put("message", "Pickup scheduled successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Get donation statistics for donor
    @GetMapping("/donor/{donorId}/stats")
    public ResponseEntity<?> getDonorStats(@PathVariable int donorId) {
        try {
            List<DonationDTO> donations = donationRepository.findByDonorId(donorId);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalDonations", donations.size());
            stats.put("pending", donations.stream().filter(d -> d.getStatus() == DonationStatus.PENDING).count());
            stats.put("confirmed", donations.stream().filter(d -> d.getStatus() == DonationStatus.CONFIRMED).count());
            stats.put("scheduled", donations.stream().filter(d -> d.getStatus() == DonationStatus.SCHEDULED).count());
            stats.put("pickedUp", donations.stream().filter(d -> d.getStatus() == DonationStatus.PICKED_UP).count());
            stats.put("inTransit", donations.stream().filter(d -> d.getStatus() == DonationStatus.IN_TRANSIT).count());
            stats.put("delivered", donations.stream().filter(d -> d.getStatus() == DonationStatus.DELIVERED).count());
            stats.put("completed", donations.stream().filter(d -> d.getStatus() == DonationStatus.COMPLETED).count());

            int totalBeneficiaries = donations.stream()
                    .mapToInt(d -> d.getBeneficiariesCount() != null ? d.getBeneficiariesCount() : 0)
                    .sum();
            stats.put("totalBeneficiaries", totalBeneficiaries);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}