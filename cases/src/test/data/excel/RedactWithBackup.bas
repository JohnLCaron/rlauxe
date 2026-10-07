Option VBASupport 1
Attribute VB_Name = "RedactWithBackup"

Sub RedactWithBackup()
    Const HEADER_ROWS As Long = 4
    Const MIN_GROUP   As Long = 10

    Dim ws           As Worksheet
    Dim lastRow      As Long
    Dim lastCol      As Long
    Dim numVoteCols  As Long
    Dim i            As Long
    Dim c            As Long
    Dim r            As Long
    Dim runStart     As Long
    Dim runCount     As Long
    Dim runKey       As String
    Dim currentKey   As String
    Dim tallies()    As Long
    Dim aggRow       As Long
    Dim btCol        As Long
    Dim ppCol        As Long
    Dim voteStartCol As Long
    Dim hasPP        As Boolean
    Dim j            As Long
    Dim hdr          As String
    Dim anyRedacted  As Boolean
    Dim currentCell  As Range

    ' Backup variables
    Dim wb           As Workbook
    Dim currentPath  As String
    Dim currentName  As String
    Dim backupName   As String
    Dim dotPos       As Long

    ' For LibreCalc, convert to .xlsm (Macro-Enabled Workbook) and save macro inside the file
    ' For Excel you can just work with the csv directly
    Set ws = ActiveSheet

    ' Only attempt to backup if the file has been saved before (has a path)
    Set wb = ws.Parent
    If wb.Path <> vbNullString Then
        currentPath = wb.Path & Application.PathSeparator
        currentName = wb.Name

        ' Find the file extension dot to split the name safely
        dotPos = InStrRev(currentName, ".")
        If dotPos > 0 Then
            backupName = Left(currentName, dotPos - 1) & "_BACKUP" & Mid(currentName, dotPos)
        Else
            backupName = currentName & "_BACKUP.csv"
        End If

        ' Save a safety copy side-by-side with your original file
        On Error Resume Next
        wb.SaveCopyAs currentPath & backupName
        On Error GoTo 0
    End If
    ' =========================================================================

    lastRow = ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
    lastCol = ws.UsedRange.Columns(ws.UsedRange.Columns.Count).Column

    ' Locate BallotType and (optionally) PrecinctPortion by header in row 4
    btCol = 0
    ppCol = 0
    For j = 1 To lastCol
        hdr = CStr(ws.Cells(HEADER_ROWS, j).Value)
        If hdr = "BallotType"      Then btCol = j
        If hdr = "PrecinctPortion" Then ppCol = j
    Next j

    If btCol = 0 Then
        MsgBox "Could not find a 'BallotType' column in row " & HEADER_ROWS & "."
        Exit Sub
    End If

    hasPP = (ppCol > 0)
    voteStartCol = btCol + 1

    If lastRow <= HEADER_ROWS Then
        MsgBox "No data rows found below the header rows."
        Exit Sub
    End If
    If voteStartCol > lastCol Then
        MsgBox "No vote columns found to the right of BallotType."
        Exit Sub
    End If

    ' =========================================================================
    ' UNIVERSAL CROSS-PLATFORM SORTING BLOCK (Excel & LibreOffice Friendly)
    ' =========================================================================
    If hasPP Then
        ' Sort primary by PrecinctPortion, then secondary by BallotType
        ws.Range(ws.Cells(HEADER_ROWS, 1), ws.Cells(lastRow, lastCol)).Sort _
            Key1:=ws.Cells(HEADER_ROWS, ppCol), Order1:=xlAscending, _
            Key2:=ws.Cells(HEADER_ROWS, btCol), Order2:=xlAscending, _
            Header:=xlYes, MatchCase:=False, Orientation:=xlTopToBottom
    Else
        ' Sort strictly by BallotType
        ws.Range(ws.Cells(HEADER_ROWS, 1), ws.Cells(lastRow, lastCol)).Sort _
            Key1:=ws.Cells(HEADER_ROWS, btCol), Order1:=xlAscending, _
            Header:=xlYes, MatchCase:=False, Orientation:=xlTopToBottom
    End If
    ' =========================================================================

    numVoteCols = lastCol - voteStartCol + 1
    ReDim tallies(1 To numVoteCols)

    Application.ScreenUpdating = False
    Application.Calculation = xlCalculationManual

    Dim dataStart As Long
    dataStart = HEADER_ROWS + 1

    runStart = dataStart
    runKey = MakeKey(ws, dataStart, btCol, ppCol, hasPP)

    ' i runs one past lastRow so the final run is always flushed
    For i = dataStart + 1 To lastRow + 1
        If i <= lastRow Then
            currentKey = MakeKey(ws, i, btCol, ppCol, hasPP)
        Else
            currentKey = vbNullString  ' sentinel — forces flush of last run
        End If

        If currentKey <> runKey Then
            runCount = i - runStart
            If runCount < MIN_GROUP Then
                ' Accumulate vote tallies and redact non-blank cells
                For r = runStart To i - 1
                    For c = 1 To numVoteCols
                        Set currentCell = ws.Cells(r, voteStartCol - 1 + c)

                        ' Count the vote if it equals 1
                        If currentCell.Value = 1 Then
                            tallies(c) = tallies(c) + 1
                        End If

                        ' Put an asterisk ONLY if the cell is not blank
                        If Trim(CStr(currentCell.Value)) <> vbNullString Then
                            currentCell.Value = "*"
                        End If
                    Next c
                Next r
                anyRedacted = True
            End If
            runStart = i
            runKey = currentKey
        End If
    Next i

    Application.ScreenUpdating = True
    Application.Calculation = xlCalculationAutomatic

    If Not anyRedacted Then
        MsgBox "No groups with fewer than " & MIN_GROUP & " rows were found. No redaction needed - exit without saving."
        Exit Sub
    End If

    ' Append aggregation row
    aggRow = lastRow + 1
    ws.Cells(aggRow, 1).Value = "AGGREGATED"
    ws.Cells(aggRow, btCol).Value = "AGGREGATED"
    For c = 1 To numVoteCols
        ws.Cells(aggRow, voteStartCol - 1 + c).Value = tallies(c)
    Next c

    ' Custom message confirming backup path
    If wb.Path <> vbNullString Then
        MsgBox "Done! Data sorted and redacted. Aggregation row added at row " & aggRow & "." & vbCrLf & vbCrLf & _
               "🔒 Safety Backup saved to folder as: " & backupName
    Else
        MsgBox "Done! Data sorted and redacted. Aggregation row added at row " & aggRow & "."
    End If
End Sub

Private Function MakeKey(ws As Worksheet, rowNum As Long, btCol As Long, ppCol As Long, hasPP As Boolean) As String
    If hasPP Then
        MakeKey = CStr(ws.Cells(rowNum, ppCol).Value) & "|" & CStr(ws.Cells(rowNum, btCol).Value)
    Else
        MakeKey = CStr(ws.Cells(rowNum, btCol).Value)
    End If
End Function
