// $Id: PrintPoolCellHelper.java,v 1.1 2006-06-05 08:51:28 patrick Exp $Cg
package diskCacheV111.services.web;

import dmg.cells.nucleus.CellInfo;

import diskCacheV111.pools.PoolCellInfo;
import diskCacheV111.pools.PoolCostInfo;
import diskCacheV111.util.HTMLWriter;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Collection;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

public class PoolInfoTableWriter
{
    private final HTMLWriter _html;

    public PoolInfoTableWriter(HTMLWriter html)
    {
        _html = html;
    }

    static private double round(double value)
    {
        return Math.floor(value * 10) / 10.0;
    }

    private void printPoolInfoRow(String cell, String domain,
                                  long total, long freespace, long precious, long removable,
                                  String... classes)
    {
        final long mb = 1024 * 1024;

        double red     = round(100 * precious / (float)total);
        double green   = round(100 * removable / (float)total);
        double yellow  = round(100 * freespace / (float)total);
        double blue    = Math.max(0, 100 - red - green - yellow);

        _html.beginRow(classes);
        _html.td("cell",     cell);
        _html.td("domain",   domain);
        _html.td("total",    total / mb);
        _html.td("free",     freespace / mb);
        _html.td("precious", precious / mb);
        _html.td("layout",
                "<div>",
                "<div class=\"layout_precious\" style=\"width: ", String.format("%.1f", red), "%\"></div>",
                "<div class=\"layout_rest\" style=\"width: ", String.format("%.1f", blue), "%\"></div>",
                "<div class=\"layout_used\" style=\"width: ", String.format("%.1f", green), "%\"></div>",
                "<div class=\"layout_free\" style=\"width: ", String.format("%.1f", yellow), "%\"></div>",
                "</div>");
        _html.endRow();
    }

    private void printPoolInfoRow(PoolCellInfo cellInfo)
    {
        PoolCostInfo.PoolSpaceInfo info =
            cellInfo.getPoolCostInfo().getSpaceInfo();

        if (cellInfo.getErrorCode() == 0) {
            printPoolInfoRow(cellInfo.getCellName(),
                             cellInfo.getDomainName(),
                             info.getTotalSpace(),
                             info.getFreeSpace(),
                             info.getPreciousSpace(),
                             info.getRemovableSpace(),
                             null, "odd");
        } else {
            _html.beginRow(null, "odd");
            _html.td("cell",      cellInfo.getCellName());
            _html.td("domain",    cellInfo.getDomainName());
            _html.td("errorcode", "[", cellInfo.getErrorCode(), "]");
            _html.td(3, "errormessage", cellInfo.getErrorMessage());
            _html.endRow();
        }
    }

    public void print(Collection<PoolCellQueryInfo> itemSet)
    {
        _html.beginTable("sortable",
                         "cell",     "CellName",
                         "domain",   "DomainName",
                         "total",    "Total Space/MB",
                         "free",     "Free Space/MB",
                         "precious", "Precious Space/MB",
                         "layout",   "<span>Layout   " +
                          "(<span class=\"layout_precious\">precious/</span>" +
                          "<span class=\"layout_used\">used/</span>" +
                          "<span class=\"layout_free\">free</span>)</span>");

        long[] spaces = sumUpSpaces(itemSet);
        printPoolInfoRow("SUM", "-",
                         spaces[0], spaces[1], spaces[2], spaces[3],
                         "total");

        for (Object i : itemSet) {
            try {
                PoolCellQueryInfo info = (PoolCellQueryInfo)i;
                CellInfo cellInfo  = info.getPoolCellInfo();
                if (info.isOk() && (cellInfo instanceof PoolCellInfo)) {
                    printPoolInfoRow((PoolCellInfo)cellInfo);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        printPoolInfoRow("SUM", "-",
                         spaces[0], spaces[1], spaces[2], spaces[3],
                         "total");
        _html.endTable();
    }

    private long[] sumUpSpaces(Collection itemSet)
    {
        long[]  result = new long[4];

        for (Object i : itemSet) {
            try {
                PoolCellQueryInfo info = (PoolCellQueryInfo)i;
                CellInfo cellInfo = info.getPoolCellInfo();
                if (info.isOk() && (cellInfo instanceof PoolCellInfo)) {
                    PoolCostInfo.PoolSpaceInfo spaceInfo =
                        ((PoolCellInfo)cellInfo).getPoolCostInfo().getSpaceInfo();
                    result[0] += spaceInfo.getTotalSpace();
                    result[1] += spaceInfo.getFreeSpace();
                    result[2] += spaceInfo.getPreciousSpace();
                    result[3] += spaceInfo.getRemovableSpace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
