package volumet.project.controller.v1;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import volumet.project.constants.ApiPath;
import volumet.project.dtos.response.RoomResponseDto;
import volumet.project.services.RoomService;

@RestController(value = "studentApi")
@RequestMapping(ApiPath.API_V1 + "/student")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StudentRestController {
    RoomService roomService;

    @GetMapping("/getRoom/{id}")
    public ResponseEntity<RoomResponseDto> getRoom(@PathVariable Long id) {
        return ResponseEntity.ofNullable(roomService.findById(id));
    }
}
