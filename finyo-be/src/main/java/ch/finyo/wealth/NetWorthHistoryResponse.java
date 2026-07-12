package ch.finyo.wealth;

import java.util.List;

public record NetWorthHistoryResponse(
        List<NetWorthHistoryPoint> points
) {}
