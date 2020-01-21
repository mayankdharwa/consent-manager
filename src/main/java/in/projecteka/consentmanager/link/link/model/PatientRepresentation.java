package in.projecteka.consentmanager.link.link.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
public class PatientRepresentation {
    private String referenceNumber;
    private String Display;
    private List<CareContextRepresentation> careContexts;
}