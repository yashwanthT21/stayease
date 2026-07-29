    package com.stayease.stay.mapper;

    import com.stayease.stay.dto.CheckOutRecordRequest;
    import com.stayease.stay.dto.CheckOutRecordResponse;
    import com.stayease.stay.entity.CheckOutRecord;
    import com.stayease.stay.enums.CheckOutStatus;

    public final class CheckOutRecordMapper {

        private CheckOutRecordMapper() {
        }

        public static CheckOutRecord toEntity(CheckOutRecordRequest request) {
            CheckOutRecord c = new CheckOutRecord();
            c.setReservationId(request.reservationId());
            apply(c, request);
            return c;
        }

        public static void updateEntity(CheckOutRecord c, CheckOutRecordRequest request) {
            apply(c, request);
        }

        private static void apply(CheckOutRecord c, CheckOutRecordRequest request) {
            c.setActualCheckOut(request.actualCheckOut());
            c.setDamageNoted(Boolean.TRUE.equals(request.damageNoted()));
            c.setDamageDescription(request.damageDescription());
            c.setDepositReleased(Boolean.TRUE.equals(request.depositReleased()));
            c.setStatus(request.status() != null ? request.status() : CheckOutStatus.CHECKED_OUT);
        }

        public static CheckOutRecordResponse toResponse(CheckOutRecord c) {
            return new CheckOutRecordResponse(
                    c.getId(),
                    c.getReservationId(),
                    c.getActualCheckOut(),
                    c.isDamageNoted(),
                    c.getDamageDescription(),
                    c.isDepositReleased(),
                    c.getStatus());
        }
    }
