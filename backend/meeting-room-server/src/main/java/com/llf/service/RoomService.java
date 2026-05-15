package com.llf.service;

import com.llf.dto.admin.room.AdminRoomDevicesDTO;
import com.llf.dto.admin.room.AdminRoomStatusDTO;
import com.llf.dto.admin.room.RoomUpsertDTO;
import com.llf.vo.room.RoomDeviceOptionVO;
import com.llf.vo.room.RoomPageDataVO;
import com.llf.vo.room.RoomPageItemVO;

import java.util.List;

public interface RoomService {
    RoomPageDataVO pageRooms(Integer currentPage,
                             Integer size,
                             String keyword,
                             String status,
                             String capacityType,
                             String location,
                             String deviceIds);

    RoomPageItemVO userDetailById(Long id);

    RoomPageDataVO adminPage(Integer currentPage,
                             Integer size,
                             String keyword,
                             String status,
                             String capacityType,
                             String location,
                             String deviceIds);

    RoomPageItemVO adminDetailById(Long id);

    List<String> locations();

    List<RoomDeviceOptionVO> deviceOptions();

    Long adminCreateV2(RoomUpsertDTO dto);

    void adminUpdateV2(Long id, RoomUpsertDTO dto);

    void adminUpdateStatus(Long id, AdminRoomStatusDTO dto);

    void adminDeleteById(Long id);

    void adminUpdateDevices(Long id, AdminRoomDevicesDTO dto);
}
