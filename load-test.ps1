param(
  [ValidateRange(30,50)][int]$Students = 30,
  [string]$BaseUrl = $(if ($env:SWIFTATTEND_URL) { $env:SWIFTATTEND_URL } else { 'http://localhost:8080' })
)
$ErrorActionPreference = 'Stop'
if (-not $env:SWIFTATTEND_TEACHER_EMAIL -or -not $env:SWIFTATTEND_TEACHER_PASSWORD) { throw 'Set SWIFTATTEND_TEACHER_EMAIL and SWIFTATTEND_TEACHER_PASSWORD for a teacher account.' }
if (-not $env:SWIFTATTEND_LOAD_LATITUDE -or -not $env:SWIFTATTEND_LOAD_LONGITUDE) { throw 'Set SWIFTATTEND_LOAD_LATITUDE and SWIFTATTEND_LOAD_LONGITUDE to the classroom anchor.' }
$client = [System.Net.Http.HttpClient]::new()
$client.BaseAddress = [uri]$BaseUrl
$client.Timeout = [TimeSpan]::FromSeconds(90)
function Post-Json($Path, $Body, $Token) {
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $Path)
  $request.Content = [System.Net.Http.StringContent]::new(($Body | ConvertTo-Json -Depth 8 -Compress), [System.Text.Encoding]::UTF8, 'application/json')
  if ($Token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
  $response = $client.SendAsync($request).GetAwaiter().GetResult()
  $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
  if (-not $response.IsSuccessStatusCode) { throw "$Path returned $([int]$response.StatusCode): $raw" }
  return ($raw | ConvertFrom-Json)
}
try {
  $teacher = Post-Json '/api/auth/login' @{ email=$env:SWIFTATTEND_TEACHER_EMAIL; password=$env:SWIFTATTEND_TEACHER_PASSWORD } $null
  $suffix = [guid]::NewGuid().ToString('N').Substring(0,8).ToUpperInvariant()
  $anchor = @{ latitude=[double]$env:SWIFTATTEND_LOAD_LATITUDE; longitude=[double]$env:SWIFTATTEND_LOAD_LONGITUDE }
  $session = Post-Json '/api/attendance/session/start' (@{ subjectCode="LT$suffix"; subjectName='Classroom Load Profile'; roomNumber='Load Test Room'; batch="LOAD-$suffix"; radiusMeters=1000 } + $anchor) $teacher.token
  $descriptor = @(1..128 | ForEach-Object { 0.01 })
  $studentTokens = [System.Collections.Generic.List[string]]::new()
  for ($i=1; $i -le $Students; $i++) {
    $email = "load-$suffix-$i@example.test"
    $account = Post-Json '/api/auth/register' @{ name="Load Student $i"; email=$email; password='SwiftAttendLoad!2026'; role='STUDENT'; enrollmentNumber="L$suffix-$('{0:D2}' -f $i)" } $null
    [void](Post-Json '/api/auth/face-descriptor' @{ faceDescriptor=$descriptor } $account.token)
    $studentTokens.Add($account.token)
  }
  $qrRequest = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, "/api/attendance/session/$($session.sessionId)/qr")
  $qrRequest.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $teacher.token)
  $qrResponse = $client.SendAsync($qrRequest).GetAwaiter().GetResult()
  if (-not $qrResponse.IsSuccessStatusCode) { throw "QR endpoint returned $([int]$qrResponse.StatusCode)" }
  $qr = $qrResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
  $pending = [System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]]::new()
  $clock = [System.Diagnostics.Stopwatch]::StartNew()
  foreach ($token in $studentTokens) {
    $body = @{ sessionId=$qr.sessionId; timestamp=$qr.timestamp; nonce=$qr.nonce; signature=$qr.signature; faceDescriptor=$descriptor; livenessPassed=$true; livenessChallenge='BLINK_TWICE'; latitude=$anchor.latitude; longitude=$anchor.longitude } | ConvertTo-Json -Depth 8 -Compress
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, '/api/attendance/verify-qr')
    $request.Content = [System.Net.Http.StringContent]::new($body, [System.Text.Encoding]::UTF8, 'application/json')
    $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
    $pending.Add($client.SendAsync($request))
  }
  [System.Threading.Tasks.Task]::WaitAll($pending.ToArray())
  $clock.Stop()
  $success = @($pending | Where-Object { $_.Result.IsSuccessStatusCode }).Count
  $failed = $Students - $success
  $rate = [math]::Round(($Students * 1000.0 / [math]::Max(1,$clock.ElapsedMilliseconds)),2)
  [pscustomobject]@{ SimulatedStudents=$Students; Accepted=$success; Failed=$failed; ConcurrentRequestWaveMs=$clock.ElapsedMilliseconds; ApproxRequestsPerSecond=$rate } | Format-List
  if ($failed -gt 0) {
    foreach ($task in $pending) { if (-not $task.Result.IsSuccessStatusCode) { "HTTP $([int]$task.Result.StatusCode): $($task.Result.Content.ReadAsStringAsync().GetAwaiter().GetResult())" } }
    exit 1
  }
} finally {
  if ($session -and $teacher) { try { [void](Post-Json '/api/attendance/session/end' @{sessionId=$session.sessionId} $teacher.token) } catch {} }
  $client.Dispose()
}
