package volumet.project.controller.v1;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import volumet.project.constants.ApiPath;
import volumet.project.dtos.request.RoomCreationRequestDto;
import volumet.project.dtos.response.RoomResponseDto;
import volumet.project.services.RoomService;

import static volumet.project.constants.ApiPath.MANAGER_API;
import static volumet.project.constants.ApiPath.MANAGER_CREATE_ROOM;
import static volumet.project.constants.ApiPath.MANAGER_MAPPING;

@RestController(value = MANAGER_API)
@RequestMapping(ApiPath.API_V1 + MANAGER_MAPPING)
@Validated
@RequiredArgsConstructor
public class ManagerRestController {
    private final RoomService roomService;

    @PostMapping(MANAGER_CREATE_ROOM)
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody RoomCreationRequestDto room) {
        roomService.createRoom(room);
    }

    @GetMapping("/getRoom/{id}")
    public ResponseEntity<RoomResponseDto> getRoom(@PathVariable Long id) {
        return ResponseEntity.ofNullable(roomService.findById(id));
    }
}
