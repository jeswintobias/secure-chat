$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080/api"

function Register-User {
    param($username, $email, $password)
    $body = @{
        username = $username
        email = $email
        password = $password
        confirmPassword = $password
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $body -ContentType "application/json"
    return $response.token
}

$rand = Get-Random
$tokenA = Register-User -username "testuserA$rand" -email "testA$rand@example.com" -password "Password123!"
$tokenB = Register-User -username "testuserB$rand" -email "testB$rand@example.com" -password "Password123!"

$headersA = @{ Authorization = "Bearer $tokenA" }
$headersB = @{ Authorization = "Bearer $tokenB" }

# User A sends request to User B
$sendBody = @{ targetUsername = "testuserB$rand" } | ConvertTo-Json
$sendResp = Invoke-RestMethod -Uri "$baseUrl/connections/send" -Method Post -Headers $headersA -Body $sendBody -ContentType "application/json"

# User B accepts request
$requestId = $sendResp.id
$acceptResp = Invoke-RestMethod -Uri "$baseUrl/connections/$requestId/accept" -Method Post -Headers $headersB -ContentType "application/json"
$convId = $acceptResp.conversationId

Write-Output "Conversation created: $convId"

# Check if User A is a member
$myConvsA = Invoke-RestMethod -Uri "$baseUrl/groups/my-conversations" -Method Get -Headers $headersA
$myConvsB = Invoke-RestMethod -Uri "$baseUrl/groups/my-conversations" -Method Get -Headers $headersB

Write-Output "User A Conversations: $($myConvsA.id)"
Write-Output "User B Conversations: $($myConvsB.id)"
