#!/bin/bash

# Planner View Count Testing Script
# Tests the view counting feature with daily deduplication

BASE_URL="http://localhost:8080"
API_URL="$BASE_URL/api/planner/md"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test results
pass_count=0
fail_count=0

# Helper function to print test results
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ PASS${NC}: $2"
        ((pass_count++))
    else
        echo -e "${RED}✗ FAIL${NC}: $2"
        ((fail_count++))
    fi
}

# Helper function to make HTTP request
http_request() {
    local method=$1
    local url=$2
    local data=$3
    local ip=$4

    if [ -n "$ip" ]; then
        curl -s -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -H "X-Forwarded-For: $ip" \
            -d "$data" \
            -w "\n%{http_code}"
    else
        curl -s -X "$method" "$url" \
            -H "Content-Type: application/json" \
            -d "$data" \
            -w "\n%{http_code}"
    fi
}

echo "================================="
echo "Planner View Count Test Suite"
echo "================================="
echo ""

# Step 1: Find or create a published planner
echo "Step 1: Looking for published planners..."
published_response=$(curl -s "$API_URL/published?size=1")
planner_count=$(echo "$published_response" | grep -o '"totalElements":[0-9]*' | cut -d':' -f2)

if [ -z "$planner_count" ] || [ "$planner_count" -eq 0 ]; then
    echo "No published planners found. Please publish at least one planner first."
    echo "You can:"
    echo "1. Create a planner via the UI"
    echo "2. Toggle it to published status via PUT /api/planner/md/{id}/publish"
    exit 1
fi

# Extract planner ID and current view count
planner_id=$(echo "$published_response" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
initial_view_count=$(echo "$published_response" | grep -o '"viewCount":[0-9]*' | head -1 | cut -d':' -f2)

echo "Found published planner:"
echo "  ID: $planner_id"
echo "  Initial view count: $initial_view_count"
echo ""

# MV1: First view increments count
echo "================================="
echo "MV1: First view increments count"
echo "================================="

response=$(http_request POST "$API_URL/$planner_id/view" "" "")
status_code=$(echo "$response" | tail -1)

if [ "$status_code" == "204" ]; then
    print_result 0 "Got 204 No Content response"

    # Verify view count increased
    sleep 1  # Small delay for DB to update
    new_response=$(curl -s "$API_URL/published?size=1")
    new_view_count=$(echo "$new_response" | grep -o '"viewCount":[0-9]*' | head -1 | cut -d':' -f2)
    expected_count=$((initial_view_count + 1))

    if [ "$new_view_count" -eq "$expected_count" ]; then
        print_result 0 "View count incremented from $initial_view_count to $new_view_count"
    else
        print_result 1 "View count should be $expected_count but got $new_view_count"
    fi
else
    print_result 1 "Expected 204 but got $status_code"
fi
echo ""

# MV2: Duplicate same day no increment
echo "================================="
echo "MV2: Duplicate same day no increment"
echo "================================="

current_view_count=$new_view_count
response=$(http_request POST "$API_URL/$planner_id/view" "" "")
status_code=$(echo "$response" | tail -1)

if [ "$status_code" == "204" ]; then
    print_result 0 "Got 204 No Content response (deduplication is silent)"

    # Verify view count DID NOT increase
    sleep 1
    check_response=$(curl -s "$API_URL/published?size=1")
    check_view_count=$(echo "$check_response" | grep -o '"viewCount":[0-9]*' | head -1 | cut -d':' -f2)

    if [ "$check_view_count" -eq "$current_view_count" ]; then
        print_result 0 "View count stayed at $check_view_count (deduplication working)"
    else
        print_result 1 "View count should stay at $current_view_count but got $check_view_count"
    fi
else
    print_result 1 "Expected 204 but got $status_code"
fi
echo ""

# MV3: Different IP increments
echo "================================="
echo "MV3: Different IP increments"
echo "================================="

current_view_count=$check_view_count
response=$(http_request POST "$API_URL/$planner_id/view" "" "192.168.1.100")
status_code=$(echo "$response" | tail -1)

if [ "$status_code" == "204" ]; then
    print_result 0 "Got 204 No Content response with different IP"

    # Verify view count increased
    sleep 1
    ip_response=$(curl -s "$API_URL/published?size=1")
    ip_view_count=$(echo "$ip_response" | grep -o '"viewCount":[0-9]*' | head -1 | cut -d':' -f2)
    expected_count=$((current_view_count + 1))

    if [ "$ip_view_count" -eq "$expected_count" ]; then
        print_result 0 "View count incremented to $ip_view_count with different IP"
    else
        print_result 1 "View count should be $expected_count but got $ip_view_count"
    fi
else
    print_result 1 "Expected 204 but got $status_code"
fi
echo ""

# MV4: Unpublished returns 404
echo "================================="
echo "MV4: Unpublished returns 404"
echo "================================="

# Try with a fake UUID (will be unpublished/non-existent)
fake_uuid="00000000-0000-0000-0000-000000000000"
response=$(http_request POST "$API_URL/$fake_uuid/view" "" "")
status_code=$(echo "$response" | tail -1)

if [ "$status_code" == "404" ]; then
    print_result 0 "Got 404 for non-existent planner"
else
    print_result 1 "Expected 404 but got $status_code"
fi
echo ""

# Summary
echo "================================="
echo "Test Summary"
echo "================================="
echo -e "${GREEN}Passed: $pass_count${NC}"
echo -e "${RED}Failed: $fail_count${NC}"

if [ $fail_count -eq 0 ]; then
    echo -e "\n${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}Some tests failed!${NC}"
    exit 1
fi
